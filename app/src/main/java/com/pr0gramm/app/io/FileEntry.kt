package com.pr0gramm.app.io

import com.google.common.io.ByteStreams
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * A simple file based entry. This one is backed by a static file and does
 * not require caching at all. Useful for local "file://" urls.
 */
internal class FileEntry(private val file: File) : Cache.Entry {
    override fun totalSize(): Int {
        return file.length().toInt()
    }

    override fun inputStreamAt(offset: Int): InputStream {
        val stream = FileInputStream(file)

        // skip to the given offset.
        ByteStreams.skipFully(stream, offset.toLong())

        return stream
    }

    override val fractionCached: Float = 1.0f

    override fun close() {}
}
