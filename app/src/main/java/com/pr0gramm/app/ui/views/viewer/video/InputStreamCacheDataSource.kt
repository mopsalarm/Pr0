package com.pr0gramm.app.ui.views.viewer.video

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.pr0gramm.app.io.Cache
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream
import java.io.InputStream

/**
 */
internal class InputStreamCacheDataSource(private val uri: Uri, private val cache: Cache) : DataSource {
    private var inputStream: InputStream? = null

    override fun open(dataSpec: DataSpec): Long {
        cache.get(uri).use { entry ->

            // get the input stream from the entry.
            inputStream = entry.inputStreamAt(dataSpec.position.toInt())

            if (dataSpec.length >= 0) {
                // limit amount to the requestet length.
                inputStream = BoundedInputStream(inputStream!!, dataSpec.length)
            }

            return entry.totalSize.toLong()
        }
    }

    override fun close() {
        if (inputStream != null) {
            IOUtils.closeQuietly(inputStream)
            inputStream = null
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) {
            return 0
        }

        return inputStream!!.read(buffer, offset, readLength)
    }

    override fun getUri(): Uri = uri
}
