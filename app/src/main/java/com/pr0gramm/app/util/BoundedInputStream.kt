package com.pr0gramm.app.util

import java.io.InputStream

private const val EOF = -1

class CountingInputStream(private val inputStream: InputStream) : InputStream() {
    var count: Long = 0
        private set

    override fun read(): Int {
        val byte = inputStream.read()
        if (byte != EOF) {
            count++
        }

        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val byteCount = inputStream.read(b, off, len)
        if (byteCount != EOF) {
            count += byteCount
        }

        return byteCount
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        count += skipped
        return skipped
    }
}

class BoundedInputStream(inputStream: InputStream, private val limit: Long) : InputStream() {
    private val inputStream = CountingInputStream(inputStream)
    private val remaining: Long = (limit - this.inputStream.count).coerceAtLeast(0)

    override fun read(): Int {
        if (remaining == 0L) {
            return EOF
        }

        return inputStream.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val remaining = len.coerceAtMost(this.remaining.toInt())
        if (remaining == 0) {
            return EOF
        }

        return inputStream.read(b, off, remaining)
    }

    override fun skip(n: Long): Long {
        if (remaining == 0L) {
            return 0
        }

        return super.skip(n.coerceAtMost(remaining))
    }

    override fun available(): Int {
        return inputStream.available().coerceAtMost(this.remaining.toInt())
    }
}

