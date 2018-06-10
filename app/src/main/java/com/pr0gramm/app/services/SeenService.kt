package com.pr0gramm.app.services

import android.content.Context
import com.google.common.primitives.UnsignedBytes
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.time
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.DeflaterOutputStream
import kotlin.experimental.or


/**
 * Very simple service to check if an item was already visited or not.
 */

class SeenService(context: Context) {
    private val lock = Any()
    private val buffer = AtomicReference<ByteBuffer>()

    @Volatile
    var dirty: Boolean = true
        private set

    init {
        doInBackground {
            try {
                val file = File(context.filesDir, "seen-posts.bits")
                buffer.set(mapByteBuffer(file))
            } catch (error: IOException) {
                logger.warn("Could not load the seen-Cache", error)
            }
        }
    }

    fun isSeen(id: Long): Boolean {
        val buffer = this.buffer.get() ?: return false

        val idx = id.toInt() / 8
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn("Id is too large")
            return false
        }

        val mask = 1 shl (7 - id % 8).toInt()
        return (UnsignedBytes.toInt(buffer.get(idx)) and mask) != 0
    }

    fun markAsSeen(id: Long) {
        val buffer = buffer.get() ?: return

        val idx = id.toInt() / 8
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn("Id is too large")
            return
        }

        // only one thread can write the buffer at a time.
        synchronized(lock) {
            val value = UnsignedBytes.toInt(buffer.get(idx))
            val updatedValue = value or (1 shl (7 - id.toInt() % 8))
            buffer.put(idx, UnsignedBytes.saturatedCast(updatedValue.toLong()))

            if (value != updatedValue) {
                dirty = true
            }
        }
    }

    /**
     * Removes the "marked as seen" status from all items.
     */
    fun clear() {
        val buffer = this.buffer.get() ?: return

        synchronized(lock) {
            logger.info("Removing all the items")
            for (idx in 0 until buffer.limit()) {
                buffer.put(idx, 0.toByte())
            }

            dirty = true
        }
    }

    // merges the other value into this one
    fun merge(other: ByteArray) {
        val buffer = this.buffer.get() ?: return

        var updated = 0

        synchronized(lock) {
            logger.time("Merging values") {
                ByteArrayInputStream(other).use { bi ->
                    DeflaterInputStream(bi).use { input ->
                        for (idx in 0 until buffer.limit()) {
                            val otherValue = input.read()
                            if (otherValue == -1)
                                break

                            // merge them by performing a bitwise 'or'
                            val previousValue = buffer.get(idx)
                            val mergedValue = previousValue or UnsignedBytes.saturatedCast(otherValue.toLong())
                            if (previousValue != mergedValue) {
                                updated++
                                buffer.put(idx, mergedValue)
                            }
                        }
                    }
                }
            }

            dirty = updated > 0
        }

        logger.info("Updated {} bytes in seen cache", updated)
    }

    fun export(): ByteArray {
        val buffer = this.buffer.get() ?: return byteArrayOf()

        return synchronized(lock) {
            dirty = false

            logger.time("Export values") {
                ByteArrayOutputStream().use { bo ->
                    Deflater(Deflater.BEST_COMPRESSION).let { def ->
                        DeflaterOutputStream(bo, def).use { out ->
                            // write the buffer to the stream
                            Channels.newChannel(out).write(buffer.asReadOnlyBuffer())
                        }

                        def.end()
                    }

                    bo.toByteArray()
                }
            }
        }
    }

    /**
     * Maps the cache into a byte buffer. The buffer is backed by the file, so
     * all changes to the buffer are written back to the file.

     * @param file The file to map into memory
     */
    private fun mapByteBuffer(file: File): ByteBuffer {
        // space for up to a few million posts
        val size = (6000000 / 8).toLong()

        logger.info("Mapping cache: {}", file)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(size)
            return raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, size)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("SeenService")
    }
}
