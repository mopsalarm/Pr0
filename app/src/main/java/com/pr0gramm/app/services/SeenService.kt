package com.pr0gramm.app.services

import android.content.Context
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.readStream
import com.pr0gramm.app.util.unsigned
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.experimental.or


/**
 * Very simple service to check if an item was already visited or not.
 */

class SeenService(context: Context) {
    private val lock = Any()
    private val buffer = AtomicReference<ByteBuffer>()

    private val logger = Logger("SeenService")

    init {
        doInBackground {
            try {
                val file = File(context.filesDir, "seen-posts.bits")
                buffer.set(mapByteBuffer(file))
            } catch (error: Exception) {
                logger.warn("Could not load the seen-Cache", error)
            }
        }
    }

    fun isSeen(id: Long): Boolean {
        val buffer = this.buffer.get() ?: return false

        val idx = id.toInt() / 8
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn { "Id is too large" }
            return false
        }

        val mask = 1 shl (7 - id % 8).toInt()
        return (buffer.get(idx).unsigned and mask) != 0
    }

    fun markAsSeen(id: Long) {
        val buffer = buffer.get() ?: return

        val idx = id.toInt() / 8
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn { "Id is too large" }
            return
        }

        // only one thread can write the buffer at a time.
        synchronized(lock) {
            val value = buffer.get(idx).unsigned
            val updatedValue = value or (1 shl (7 - id.toInt() % 8))
            buffer.put(idx, updatedValue.toByte())
        }
    }

    /**
     * Removes the "marked as seen" status from all items.
     */
    fun clear() {
        val buffer = this.buffer.get() ?: return

        synchronized(lock) {
            logger.info { "Removing all the items" }
            for (idx in 0 until buffer.limit()) {
                buffer.put(idx, 0.toByte())
            }
        }
    }

    // Merges the other value into this one. Returns true, if the
    // other value equals the current state and no merging
    // was needed at all.
    fun checkEqualAndMerge(other: ByteArray): Boolean {
        val buffer = this.buffer.get() ?: return false

        var totalCount = 0
        var diffCount = 0

        synchronized(lock) {
            logger.time("Merging values") {
                ByteArrayInputStream(other).use { bi ->
                    InflaterInputStream(bi).use { input ->
                        val source = buffer.duplicate()
                        val target = buffer.duplicate()

                        readStream(input) { bytes, read ->
                            val stepSize = read.coerceAtMost(source.remaining())

                            var updatedInStep = 0
                            for (idx in 0 until stepSize) {
                                val otherValue = bytes[idx]
                                val previousValue = source.get()

                                val mergedValue = otherValue or previousValue

                                if (previousValue != mergedValue) {
                                    bytes[idx] = mergedValue
                                    updatedInStep++
                                }

                                if (previousValue != otherValue) {
                                    // could also be a local change
                                    diffCount++
                                }
                            }

                            if (updatedInStep > 0) {
                                target.put(bytes, 0, stepSize)
                            } else {
                                target.position(target.position() + stepSize)
                            }

                            totalCount += stepSize
                        }
                    }
                }
            }

        }

        logger.info { "Changes in $diffCount out of $totalCount bytes in seen cache" }
        return diffCount == 0 && totalCount == buffer.limit()
    }

    /**
     * Workaround to reset the lower n bytes.
     */
    fun clearUpTo(n: Int) {
        val buffer = this.buffer.get() ?: return

        logger.info { "Setting the first $n bits to zero." }

        synchronized(lock) {
            for (idx in 0 until n.coerceAtMost(buffer.limit())) {
                buffer.put(idx, 0)
            }
        }
    }

    fun export(): ByteArray {
        val buffer = this.buffer.get() ?: return byteArrayOf()

        return synchronized(lock) {
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

        logger.info { "Mapping cache: $file" }
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(size)
            return raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, size)
        }
    }
}
