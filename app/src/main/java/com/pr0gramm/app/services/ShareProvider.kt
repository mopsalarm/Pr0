package com.pr0gramm.app.services

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableMap
import com.google.common.io.BaseEncoding
import com.google.common.io.ByteStreams
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.BackgroundScheduler
import org.slf4j.LoggerFactory
import rx.Single
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 */
class ShareProvider : ContentProvider() {

    @Inject
    internal lateinit var cache: Cache

    override fun onCreate(): Boolean {
        Dagger.appComponent(context).inject(this)
        return true
    }

    override fun query(uri: Uri, projection_: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {

        val projection = projection_ ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        // we get the file size on some background thread to make android happy.
        val fileSize = Single
                .fromCallable { getSizeForUri(uri) }
                .subscribeOn(BackgroundScheduler.instance())
                .onErrorResumeNext { null }
                .toBlocking()
                .value()

        // adapted from FileProvider.query
        val cols = arrayOfNulls<String>(projection.size)
        val values = arrayOfNulls<Any>(projection.size)
        projection.forEachIndexed { idx, col ->
            cols[idx] = col
            values[idx] = null

            if (OpenableColumns.DISPLAY_NAME == col) {
                values[idx] = decode(uri).lastPathSegment
            }

            if (OpenableColumns.SIZE == col) {
                values[idx] = fileSize
            }
        }

        val cursor = MatrixCursor(cols, 1)
        cursor.addRow(values)
        return cursor
    }

    @Throws(IOException::class)
    private fun getSizeForUri(uri: Uri): Long {
        val url = decode(uri).toString()
        return cache.get(Uri.parse(url)).totalSize().toLong()
    }

    override fun getType(uri: Uri): String? {
        return guessMimetype(decode(uri))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String?> {
        return arrayOf(guessMimetype(decode(uri)))
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val url = decode(uri).toString()
        val mimeType = guessMimetype(decode(uri))

        return openPipeHelper<Any>(uri, mimeType, null, null) { output, uri1, mimeType, opts, args ->
            try {
                if (url.matches("https?://.*".toRegex())) {
                    cache.get(Uri.parse(url)).inputStreamAt(0).use { source ->
                        // stream the data to the caller
                        ByteStreams.copy(source, FileOutputStream(output.fileDescriptor))
                    }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(url)).use { source ->
                        ByteStreams.copy(source, FileOutputStream(output.fileDescriptor))
                    }
                }
            } catch (error: IOException) {
                // do nothing
                try {
                    logger.warn("Could not stream data to client", error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        output.closeWithError(error.toString())
                    } else {
                        output.close()
                    }

                } catch (err: IOException) {
                    logger.warn("Error trying to close the stream")
                }
            }
        }
    }

    /**
     * Decodes the received url
     */
    private fun decode(uri: Uri): Uri {
        val decoder = BaseEncoding.base64Url()
        return Uri.parse(String(decoder.decode(uri.lastPathSegment), Charsets.UTF_8))
    }

    companion object {
        private val logger = LoggerFactory.getLogger("ShareProvider")

        /**
         * Returns an uri for the given item to share that item with other apps.
         */
        fun getShareUri(context: Context, item: FeedItem): Uri {
            val uri = getMediaUri(context, item).toString()
            val path = BaseEncoding.base64Url().encode(uri.toByteArray(Charsets.UTF_8))
            return Uri.Builder()
                    .scheme("content")
                    .authority(BuildConfig.APPLICATION_ID + ".ShareProvider")
                    .path(path)
                    .build()
        }

        fun guessMimetype(context: Context, item: FeedItem): String {
            return guessMimetype(getMediaUri(context, item))
        }

        private fun getMediaUri(context: Context, item: FeedItem): Uri {
            return UriHelper.of(context).media(item)
        }

        private fun guessMimetype(uri: Uri): String {
            val url = uri.toString()
            if (url.length < 4)
                return "application/binary"

            val extension = url.substring(url.length - 4).toLowerCase()
            return EXT_MIMETYPE_MAP[extension] ?: "application/binary"
        }

        private val EXT_MIMETYPE_MAP = ImmutableMap.builder<String, String>()
                .put(".png", "image/png")
                .put(".jpg", "image/jpg")
                .put("jpeg", "image/jpeg")
                .put("webm", "video/webm")
                .put(".mp4", "video/mp4")
                .put(".gif", "image/gif")
                .build()
    }
}
