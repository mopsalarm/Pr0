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
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.decodeBase64String
import com.pr0gramm.app.encodeBase64
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.kodein
import com.pr0gramm.app.util.logger
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.erased.instance
import java.io.FileOutputStream
import java.io.IOException

/**
 */
class ShareProvider : ContentProvider(), KodeinAware {
    override val kodein: Kodein by lazy { context.kodein }

    private val cache by instance<Cache>()

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(uri: Uri, projection_: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {

        val projection = projection_ ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        val fileSize = getSizeForUri(uri)

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

    private fun getSizeForUri(uri: Uri): Long {
        val url = decode(uri).toString()
        return cache.get(Uri.parse(url)).totalSize.toLong()
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

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val url = decode(uri).toString()
        val mimeType = guessMimetype(decode(uri))

        return openPipeHelper<Any>(uri, mimeType, null, null) { output, _, _, _, _ ->
            try {
                if (url.matches("https?://.*".toRegex())) {
                    cache.get(Uri.parse(url)).inputStreamAt(0).use { source ->
                        // stream the data to the caller
                        source.copyTo(FileOutputStream(output.fileDescriptor))
                    }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(url)).use { source ->
                        source.copyTo(FileOutputStream(output.fileDescriptor))
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
        return Uri.parse(uri.lastPathSegment.decodeBase64String(urlSafe = true))
    }

    companion object {
        private val logger = logger("ShareProvider")

        /**
         * Returns an uri for the given item to share that item with other apps.
         */
        fun getShareUri(context: Context, item: FeedItem): Uri {
            val uri = getMediaUri(context, item).toString()
            val path = uri.toByteArray().encodeBase64(urlSafe = true)
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

        private val EXT_MIMETYPE_MAP = mapOf(
                ".png" to "image/png",
                ".jpg" to "image/jpg",
                "jpeg" to "image/jpeg",
                "webm" to "video/webm",
                ".mp4" to "video/mp4",
                ".gif" to "image/gif")
    }
}
