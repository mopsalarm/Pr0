package com.pr0gramm.app.io

import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.skipSimple
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * A simple file based entry. This one is backed by a static file and does
 * not require caching at all. Useful for local "file://" urls.
 */
internal class FileEntry(override val file: File) : Cache.Entry {
    private val logger = Logger("FileEntry")

    override val totalSize: Int
        get() {
            return file.length().toInt()
        }

    override fun inputStreamAt(offset: Int): InputStream {
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            logger.warn { "Could not update timestamp on $file" }
        }

        val stream = FileInputStream(file)

        // skip to the given offset.
        stream.skipSimple(offset.toLong())

        return stream
    }

    override val fractionCached: Float = 1.0f

    override fun close() {}
}
