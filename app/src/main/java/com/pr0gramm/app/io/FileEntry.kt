package com.pr0gramm.app.io

import com.google.common.io.ByteStreams
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * A simple file based entry. This one is backed by a static file and does
 * not require caching at all. Useful for local "file://" urls.
 */
internal class FileEntry(override val file: File) : Cache.Entry {
    private val logger = LoggerFactory.getLogger("FileEntry")

    override fun totalSize(): Int {
        return file.length().toInt()
    }

    override fun inputStreamAt(offset: Int): InputStream {
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            logger.warn("Could not update timestamp on {}", file)
        }

        val stream = FileInputStream(file)

        // skip to the given offset.
        ByteStreams.skipFully(stream, offset.toLong())

        return stream
    }

    override val fractionCached: Float = 1.0f

    override fun close() {}
}
