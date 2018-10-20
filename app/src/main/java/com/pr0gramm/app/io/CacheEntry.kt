package com.pr0gramm.app.io

import android.net.Uri
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.logToCrashlytics
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A entry that is hold by the [Cache].
 */
internal class CacheEntry(private val httpClient: OkHttpClient, override val file: File, private val uri: Uri) : Cache.Entry {
    private val logger = logger("CacheEntry")

    private val lock = ReentrantLock()
    private val writtenUpdated = lock.newCondition()

    private val refCount = AtomicInteger()

    @Volatile
    private var fp: RandomAccessFile? = null

    @Volatile
    private var totalSizeField: Int = 0

    @Volatile
    private var written: Int = 0

    @Volatile
    private var cacheWriter: CacheWriter? = null

    fun read(pos: Int, data: ByteArray, offset: Int, amount: Int): Int {
        // Always succeed when reading 0 bytes.
        if (amount == 0) {
            return 0
        }

        lock.withLock {
            val fp = ensureInitialized()

            // if we are at the end of the file, we need to signal that
            if (pos >= totalSizeField) {
                return -1
            }
            // check how much we can actually read at most!
            val amount = Math.min(pos + amount, totalSizeField) - pos

            // wait for the data to be there
            expectCached(pos + amount)
            seek(fp, pos)

            // now try to read the bytes we requested
            val byteCount = read(fp, data, offset, amount)

            // check if we got as much bytes as we wanted to.
            if (byteCount != amount) {
                logToCrashlytics(
                        EOFException(String.format("Expected to read %d bytes at %d, but got only %d. Cache entry: %s", amount, pos, byteCount, this)))
            }

            return byteCount
        }
    }


    override val totalSize: Int
        get() {
            ensureInitialized()
            return totalSizeField
        }

    private fun write(data: ByteArray, offset: Int, amount: Int) {
        lock.withLock {
            val fp = ensureInitialized()

            // only really write if we have a positive amount here.
            if (amount > 0) {
                seek(fp, written)
                fp.write(data, offset, amount)
                written += amount
            }

            // tell the readers about the new data.
            writtenUpdated.signalAll()
        }
    }

    /**
     * Waits until at least the given amount of data is written.
     */
    private fun expectCached(requiredCount: Int) {
        try {
            while (written < requiredCount) {
                ensureCaching()
                writtenUpdated.await(250, TimeUnit.MILLISECONDS)
            }
        } catch (err: InterruptedException) {
            throw InterruptedIOException("Waiting for bytes was interrupted.")
        }
    }

    private fun seek(fp: RandomAccessFile, pos: Int) {
        fp.seek((PAYLOAD_OFFSET + pos).toLong())
    }

    private fun ensureInitialized(): RandomAccessFile {
        lock.withLock {
            // we are initialized if we already have a opened file.
            val fp = fp
            if (fp != null) {
                return fp
            }

            logger.debug { "Entry needs to be initialized: ${this}" }
            return initialize()
        }
    }

    /**
     * Returns true, if the entry is fully cached.
     * You need to hold the lock to call this method.
     */
    private fun fullyCached(): Boolean {
        return written >= totalSizeField
    }

    /**
     * Will be called if we need to initialize the file. If this is called, we can expect
     * the entry to hold its own lock.
     */
    private fun initialize(): RandomAccessFile {
        // ensure that the parent directory exists.
        val parentFile = file.parentFile
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                logger.warn { "Could not create parent directory." }
            }
        }

        // we expect the hash of the filename as the checksum.
        val expectedChecksum = file.name.hashCode()

        // open the file in read/write mode, creating it if it did not exist.
        val fp = RandomAccessFile(file, "rwd")
        this.fp = fp
        try {
            // get the length of the file and the checksum to test if we just created it,
            // or if it already contains data.
            val length = fp.length().toInt()
            val fileIsValid = length >= 8 && fp.readInt() == expectedChecksum

            if (fileIsValid) {
                logger.debug { "Found already cached file, loading metadata." }

                // read the total size from the file now.
                // We've previously read the first four bytes (checksum).
                totalSizeField = fp.readInt()
                written = Math.max(0, length - PAYLOAD_OFFSET)

                if (written > totalSizeField) {
                    // okay, someone fucked up! :/
                    logToCrashlytics(IOException("written=$written greater than totalSize=$totalSizeField"))

                    // invalidate the file and try again.
                    fp.setLength(0)
                    fp.close()

                    return initialize()
                }

            } else {
                logger.debug { "Entry is new, no data is previously cached." }

                // write header at the beginning of the file
                fp.setLength(0)

                // we can not have written anything yet.
                written = 0

                // start caching now.
                totalSizeField = resumeCaching(0)

                fp.writeInt(expectedChecksum)
                fp.writeInt(totalSizeField)
            }

            logger.debug { "Initialized entry ${this}" }
            return fp

        } catch (err: Exception) {
            // resetting fp on error.
            fp.closeQuietly()

            // cleanup
            reset()

            throw err
        }
    }

    private fun reset() {
        logger.debug { "Resetting entry ${this}" }
        this.fp = null
        this.written = 0
        this.totalSizeField = 0

        this.cacheWriter = null
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet and currently not caching.
     */
    private fun ensureCaching() {
        if (cacheWriter == null && !fullyCached()) {
            logger.debug { "Caching will start on entry ${this}" }
            resumeCaching(written)
        }
    }

    private fun resumeCaching(offset: Int): Int {
        lock.withLock {
            var writer = cacheWriter
            if (writer == null) {
                writer = CacheWriter().also { writer ->
                    this.cacheWriter = writer
                    doInBackground { writer.resumeCaching(offset) }
                }
            }

            return try {
                writer.size.get()
            } catch(err: ExecutionException) {
                // throw the real error, not the wrapped one.
                throw err.cause ?: err
            }
        }
    }

    private inner class CacheWriter {
        val canceled get() = cacheWriter !== this

        val size = SettableFuture<Int>()

        /**
         * This method is called from the caching thread once caching stops.
         */
        private fun cachingStopped() {
            logger.debug { "Caching stopped on entry ${this}" }
            lock.withLock {
                if (!canceled) {
                    close()
                    cacheWriter = null
                }

                // If there are any readers, we need to notify them, so caching will be
                // re-started if needed
                writtenUpdated.signalAll()
            }
        }

        fun resumeCaching(offset: Int) {
            incrementRefCount()
            try {
                val request = Request.Builder()
                        .url(uri.toString())
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .header("Range", String.format("bytes=%d-", offset))
                        .build()

                logger.debug { "Resume caching for ${this} starting at $offset" }
                val response = httpClient.newCall(request).execute()
                try {
                    val body = response.body()!!
                    when {
                        response.code() == 200 -> written = 0
                        response.code() == 403 -> throw IOException("Not allowed to read file, are you on a public wifi?")
                        response.code() == 404 -> throw FileNotFoundException("File not found at " + response.request().url())
                        body.contentLength() < 0L -> throw IOException("Content length not defined.")
                        response.code() != 206 -> throw IOException("Expected status code 2xx, got " + response.code())
                    }

                    size.setValue(body.contentLength().toInt())

                } catch (err: Exception) {
                    response.close()
                    throw err
                }

                // read the response
                writeResponseToEntry(response)

            } catch (err: Exception) {
                logger.error { "Error in caching thread" }
                size.setError(err)

            } finally {
                cachingStopped()
            }
        }

        /**
         * Writes the response to the file. If [fp] disappears, we log a warning
         * and then we just return.
         */
        private fun writeResponseToEntry(response: Response) {
            response.body()?.byteStream()?.use { stream ->
                readStream(stream) { buffer, byteCount ->
                    lock.withLock {
                        if (canceled) {
                            logger.info { "Caching canceled, stopping now." }
                            return
                        }

                        if (fp == null) {
                            logger.warn { "Error during caching, the file-handle went away: ${this}" }
                            return
                        }

                        write(buffer, 0, byteCount)
                    }
                }
            }
        }
    }

    /**
     * Increment the refCount
     */
    fun incrementRefCount(): CacheEntry {
        refCount.incrementAndGet()
        return this
    }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    override fun close() {
        this.refCount.decrementAndGet()
        lock.withLock {
            // reset the ref count in case of errors.
            if (refCount.get() < 0) {
                refCount.set(0)
            }

            // close if ref count is zero.
            if (this.refCount.get() <= 0 && this.fp != null) {
                logger.debug { "Closing cache file for entry ${this} now." }
                fp.closeQuietly()
                reset()
            }
        }
    }

    protected fun finalize() = close()

    override fun inputStreamAt(offset: Int): InputStream {
        // update the time stamp if the cache file already exists.
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            logger.warn { "Could not update timestamp on $file" }
        }

        return EntryInputStream(incrementRefCount(), offset)
    }

    override val fractionCached: Float get() {
        if (totalSizeField > 0) {
            return written / totalSizeField.toFloat()
        } else {
            return -1f
        }
    }

    /**
     * Returns the number of bytes that are available too read without caching
     * from the given position.
     */
    private fun availableStartingAt(position: Int): Int {
        lock.withLock {
            return Math.max(0, written - position)
        }
    }

    override fun toString(): String {
        lock.withLock {
            return "Entry(written=$written, totalSize=${totalSizeField.takeIf { it > 0 }}, " +
                    "caching=${cacheWriter != null}, refCount=${refCount.get()}, " +
                    "fullyCached=${fullyCached()}, uri=$uri)"
        }
    }

    private class EntryInputStream internal constructor(private val entry: CacheEntry, private var position: Int) : InputStream() {
        private var mark: Int = 0

        override fun read(): Int {
            throw UnsupportedOperationException("read() not implemented.")
        }

        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            val byteCount = entry.read(position, bytes, off, len)
            if (byteCount > 0) {
                position += byteCount
            }

            return byteCount
        }

        override fun skip(amount: Long): Long {
            if (amount < 0) {
                return 0
            }

            val skipped = Math.min(entry.totalSize.toLong(), position + amount) - position
            position += skipped.toInt()
            return skipped
        }

        override fun close() {
            entry.close()
        }

        override fun available(): Int {
            return entry.availableStartingAt(position)
        }

        override fun markSupported(): Boolean {
            return true
        }

        override fun mark(readlimit: Int) {
            mark = position
        }

        override fun reset() {
            position = mark
        }
    }

    /**
     * Reads the given number of bytes from the current position of the stream
     * if possible. The method returns the numbers of bytes actually read.
     */
    private fun read(fp: RandomAccessFile, data: ByteArray, offset: Int, amount: Int): Int {
        var totalCount = 0

        do {
            val count = fp.read(data, offset + totalCount, amount - totalCount)
            if (count < 0) {
                break
            }

            totalCount += count
        } while (totalCount < amount)

        return totalCount
    }

    companion object {
        private val PAYLOAD_OFFSET = 16
    }
}
