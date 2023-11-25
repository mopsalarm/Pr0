package com.pr0gramm.app.ui.views.viewer.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.BoundedInputStream
import com.pr0gramm.app.util.closeQuietly
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * A data source that uses a Cache as a data source.
 */
@OptIn(UnstableApi::class)
internal class InputStreamCacheDataSource(private val cache: Cache) : BaseDataSource(true) {
    private var opened: Boolean = false
    private var _uri: Uri? = null

    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        _uri = dataSpec.uri

        transferInitializing(dataSpec)

        cache.get(dataSpec.uri).use { entry ->
            // get the input stream from the entry.
            val inputStream = entry.inputStreamAt(dataSpec.position.toInt())
            this.inputStream = inputStream

            // gets the size of the file. This also initializes the cache entry.
            val totalSize = entry.totalSize.toLong()

            if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                this.bytesRemaining = totalSize - dataSpec.position
            } else {
                // limit amount to the requested length.
                this.inputStream = BoundedInputStream(inputStream, dataSpec.length)
                this.bytesRemaining = dataSpec.length
            }

            // reduce read calls to file
            this.inputStream = BufferedInputStream(this.inputStream, 1024 * 64)

            opened = true

            // everything looks fine, inform listeners about data transfer
            transferStarted(dataSpec)

            return bytesRemaining
        }
    }

    override fun close() {
        _uri = null

        if (inputStream != null) {
            inputStream.closeQuietly()
            inputStream = null
        }

        if (opened) {
            opened = false
            transferEnded()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) {
            return 0
        }

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong())
            readLength else Math.min(bytesRemaining, readLength.toLong()).toInt()

        // read from input stream
        val stream = inputStream ?: throw IllegalStateException("DataSource is not open.")
        val bytesTransferred = stream.read(buffer, offset, bytesToRead)

        if (bytesTransferred == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                // End of stream reached having not read sufficient data.
                throw EOFException()
            }

            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesTransferred.toLong()
        }

        bytesTransferred(bytesTransferred)

        return bytesTransferred
    }

    override fun getUri(): Uri? = _uri
}
