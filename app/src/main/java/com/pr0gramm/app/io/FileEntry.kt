package com.pr0gramm.app.io

import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.skipSimple
import com.pr0gramm.app.util.updateTimestamp
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * A simple file based entry. This one is backed by a static file and does
 * not require caching at all. Useful for local "file://" urls.
 */
class FileEntry(override val file: File) : Cache.Entry {
    private val logger = Logger("FileEntry")

    override val totalSize: Int by lazy {
        if (!file.updateTimestamp())
            throw IllegalStateException("Cached file is missing: $file")

        file.length().toInt()
    }

    override fun inputStreamAt(offset: Int): InputStream {
        if (file.exists() && !file.updateTimestamp()) {
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
