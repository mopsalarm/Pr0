package com.pr0gramm.app.io

import android.net.Uri
import com.google.common.base.MoreObjects
import com.google.common.io.Closeables
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.AndroidUtility.doInBackground
import com.pr0gramm.app.util.readStream
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * A entry that is hold by the [Cache].
 */
internal class CacheEntry(private val httpClient: OkHttpClient, private val file: File, private val uri: Uri) : Cache.Entry {
    private val logger = LoggerFactory.getLogger("CacheEntry")

    private val lock = java.lang.Object()
    private val refCount = AtomicInteger()

    @Volatile private var fp: RandomAccessFile? = null
    @Volatile private var totalSize: Int = 0
    @Volatile private var written: Int = 0
    @Volatile private var caching: Boolean = false

    fun read(pos: Int, data: ByteArray, offset: Int, amount: Int): Int {
        // Always succeed when reading 0 bytes.
        if (amount == 0) {
            return 0
        }

        synchronized(lock) {
            val fp = ensureInitialized()

            // if we are at the end of the file, we need to signal that
            if (pos >= totalSize) {
                return -1
            }

            // check how much we can actually read at most!
            val amount = Math.min(pos + amount, totalSize) - pos


            // wait for the data to be there
            expectCached(pos + amount)
            seek(fp, pos)

            // now try to read the bytes we requested
            val byteCount = read(fp, data, offset, amount)

            // check if we got as much bytes as we wanted to.
            if (byteCount != amount) {
                AndroidUtility.logToCrashlytics(
                        EOFException(String.format("Expected to read %d bytes at %d, but got only %d. Cache entry: %s", amount, pos, byteCount, this)))
            }

            return byteCount
        }
    }


    override fun totalSize(): Int {
        ensureInitialized()
        return totalSize
    }

    private fun write(data: ByteArray, offset: Int, amount: Int) {
        synchronized(lock) {
            val fp = ensureInitialized()

            // only really write if we have a positive amount here.
            if (amount > 0) {
                seek(fp, written)
                fp.write(data, offset, amount)
                written += amount
            }

            // tell the readers about the new data.
            lock.notifyAll()
        }
    }

    /**
     * Waits until at least the given amount of data is written.
     */
    @Throws(IOException::class)
    private fun expectCached(requiredCount: Int) {
        try {
            while (written < requiredCount) {
                ensureCaching()
                lock.wait(250)
            }
        } catch (err: InterruptedException) {
            throw InterruptedIOException("Waiting for bytes was interrupted.")
        }

    }

    private fun seek(fp: RandomAccessFile, pos: Int) {
        fp.seek((PAYLOAD_OFFSET + pos).toLong())
    }

    private fun ensureInitialized(): RandomAccessFile {
        synchronized(lock) {
            // we are initialized if we already have a opened file.
            val fp = fp
            if (fp != null) {
                return fp
            }

            logger.debug("Entry needs to be initialized: {}", this)
            return initialize()
        }
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet and currently not caching.
     */
    @Throws(IOException::class)
    private fun ensureCaching() {
        if (!caching && !fullyCached()) {
            logger.debug("Caching will start on entry {}", this)
            resumeCaching(written)
        }
    }

    private fun cachingStarted() {
        logger.debug("Caching starts now.")

        synchronized(lock) {
            incrementRefCount()
            caching = true
        }
    }

    /**
     * This method is called from the caching thread once caching stops.
     */
    private fun cachingStopped() {
        logger.debug("Caching stopped on entry {}", this)
        synchronized(lock) {
            if (caching) {
                caching = false
                close()
            }

            // If there are any readers, we need to notify them, so caching will be
            // re-started if needed
            lock.notifyAll()
        }
    }

    /**
     * Returns true, if the entry is fully cached.
     * You need to hold the lock to call this method.
     */
    private fun fullyCached(): Boolean {
        return written >= totalSize
    }


    /**
     * Will be called if we need to initialize the file. If this is called, we can expect
     * the entry to hold its own lock.
     */
    @Throws(IOException::class)
    private fun initialize(): RandomAccessFile {
        // ensure that the parent directory exists.
        val parentFile = file.parentFile
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                logger.warn("Could not create parent directory.")
            }
        }

        // we expect the hash of the filename as the checksum.
        val expectedChecksum = file.name.hashCode()

        // open the file in read/write mode, creating it if it did not exist.
        val fp = RandomAccessFile(file, "rw")
        this.fp = fp
        try {
            // get the length of the file and the checksum to test if we just created it,
            // or if it already contains data.
            val length = fp.length().toInt()
            val fileIsValid = length >= 8 && fp.readInt() == expectedChecksum

            if (fileIsValid) {
                logger.debug("Found already cached file, loading metadata.")

                // read the total size from the file now.
                // We've previously read the first four bytes (checksum).
                totalSize = fp.readInt()
                written = Math.max(0, length - PAYLOAD_OFFSET)
            } else {
                logger.debug("Entry is new, no data is previously cached.")
                // we can not have written anything yet.
                written = 0

                // start caching now.
                totalSize = resumeCaching(0)

                // write header at the beginning of the file
                fp.channel.truncate(0)
                fp.writeInt(expectedChecksum)
                fp.writeInt(totalSize)
            }

            logger.debug("Initialized entry {}", this)
            return fp

        } catch (err: IOException) {
            // resetting fp on error.
            Closeables.close(fp, true)
            this.fp = null

            throw err
        }
    }

    private fun writeResponseToEntry(response: Response) {
        try {
            response.body().byteStream().use { stream ->
                readStream(stream) { buffer, byteCount ->
                    write(buffer, 0, byteCount)
                }

                // sync file to disk
                fp!!.fd.sync()

            }
        } catch (error: IOException) {
            logger.error("Could not buffer the complete response.", error)

        } finally {
            cachingStopped()
        }
    }

    private fun resumeCaching(offset: Int): Int {
        cachingStarted()

        try {
            val request = Request.Builder()
                    .url(uri.toString())
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header("Range", String.format("bytes=%d-", offset))
                    .build()

            logger.debug("Resume caching for {}", this)
            val response = httpClient.newCall(request).execute()
            try {
                when {
                    response.code() == 200 -> written = 0
                    response.code() == 403 -> throw IOException("Not allowed to read file, are you on a public wifi?")
                    response.code() == 404 -> throw FileNotFoundException("File not found at " + response.request().url())
                    response.code() != 206 -> throw IOException("Expected status code 206, got " + response.code())
                }
            } catch (err: Exception) {
                response.close()
                throw err
            }

            // read the response in some other thread.
            doInBackground { writeResponseToEntry(response) }

            return response.body().contentLength().toInt()

        } catch (err: IOException) {
            cachingStopped()
            throw err
        } catch (err: RuntimeException) {
            cachingStopped()
            throw err
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
     * Deletes the file if it is currently closed.
     */
    fun deleteIfClosed(): Boolean {
        synchronized(lock) {
            if (fp != null) {
                return false
            }

            if (!file.delete()) {
                logger.warn("Could not delete file {}", file)
                return false
            }

            // deletion went good!
            return true
        }
    }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    override fun close() {
        this.refCount.decrementAndGet()
        synchronized(lock) {
            // reset the ref count in case of errors.
            if (refCount.get() < 0) {
                refCount.set(0)
            }

            // close if ref count is zero.
            if (this.refCount.get() <= 0 && this.fp != null) {
                logger.debug("Closing cache file for entry {} now.", this)

                try {
                    this.fp!!.close()
                } catch (ignored: IOException) {
                }

                this.fp = null
            }
        }
    }

    override fun inputStreamAt(offset: Int): InputStream {
        // update the time stamp if the cache file already exists.
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            logger.warn("Could not update timestamp on {}", file)
        }

        return EntryInputStream(incrementRefCount(), offset)
    }

    override val fractionCached: Float get() {
        if (totalSize > 0) {
            return written / totalSize.toFloat()
        } else {
            return -1f
        }
    }

    /**
     * Returns the number of bytes that are available too read without caching
     * from the given position.
     */
    private fun availableStartingAt(position: Int): Int {
        synchronized(lock) {
            return Math.max(0, written - position)
        }
    }

    override fun toString(): String {
        synchronized(lock) {
            return MoreObjects.toStringHelper(this)
                    .add("written", written)
                    .add("totalSize", totalSize)
                    .add("caching", caching)
                    .add("refCount", refCount.get())
                    .add("fullyCached", fullyCached())
                    .add("uri", uri)
                    .toString()
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

            val skipped = Math.min(entry.totalSize().toLong(), position + amount) - position
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
