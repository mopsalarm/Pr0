package com.pr0gramm.app.services

import android.content.Context
import com.google.common.primitives.UnsignedBytes
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.ignoreException
import com.pr0gramm.app.util.subscribeOnBackground
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.DeflaterOutputStream


/**
 * Very simple service to check if an item was already visited or not.
 */

class SeenService(context: Context) {
    private val lock = Any()
    private val buffer = AtomicReference<ByteBuffer>()

    init {
        doInBackground {
            try {
                val file = File(context.filesDir, "seen-posts.bits")
                buffer.set(mapByteBuffer(file))
            } catch (error: IOException) {
                logger.warn("Could not load the seen-Cache", error)
            }
        }

        Observable.just(1).delay(5, TimeUnit.SECONDS).subscribeOnBackground().subscribe {
            ignoreException {
                val size = export().size.toLong()
                Track.seenPostsSize(size)
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
            var value = UnsignedBytes.toInt(buffer.get(idx))
            value = value or (1 shl (7 - id.toInt() % 8))
            buffer.put(idx, UnsignedBytes.saturatedCast(value.toLong()))
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
        }
    }

    fun export(): ByteArray {
        val buffer = this.buffer.get() ?: return byteArrayOf()

        return synchronized(lock) {
            val bo = ByteArrayOutputStream()
            DeflaterOutputStream(bo).use { out ->
                for (idx in 0 until buffer.limit()) {
                    out.write(buffer.get(idx).toInt())
                }
            }

            bo.toByteArray()
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
