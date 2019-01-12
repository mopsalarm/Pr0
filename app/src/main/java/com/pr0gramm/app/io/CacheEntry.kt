package com.pr0gramm.app.io

import android.app.Application
import android.net.Uri
import com.pr0gramm.app.Stats
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
internal class CacheEntry(
        private val context: Application,
        private val httpClient: OkHttpClient,
        override val file: File,
        private val uri: Uri) : Cache.Entry {

    private val logger = Logger("CacheEntry")

    private val lock = ReentrantLock()
    private val writtenUpdated = lock.newCondition()

    private val refCount = AtomicInteger()

    @Volatile
    private var fp: RandomAccessFile? = null

    @Volatile
    private var totalSizeValue: Int = 0

    @Volatile
    private var written: Int = 0

    @Volatile
    private var cacheWriter: CacheWriter? = null

    // delegate all calls to this delegate if set
    private var delegate: FileEntry? = null

    fun read(pos: Int, data: ByteArray, offset: Int, amount: Int): Int {
        // Always succeed when reading 0 bytes.
        if (amount == 0) {
            return 0
        }

        lock.withLock {
            val fp = ensureInitialized()

            // if we are at the end of the file, we need to signal that
            if (pos >= totalSizeValue) {
                return -1
            }

            // check how much we can actually read at most!
            val amountToRead = Math.min(pos + amount, totalSizeValue) - pos

            // wait for the data to be there
            expectCached(pos + amountToRead)
            seek(fp, pos)

            // now try to read the bytes we requested
            val byteCount = read(fp, data, offset, amountToRead)

            // check if we got as many bytes as we wanted to.
            if (byteCount != amountToRead) {
                logToCrashlytics(
                        EOFException(String.format("Expected to read %d bytes at %d, but got only %d. Cache entry: %s", amountToRead, pos, byteCount, this)))
            }

            return byteCount
        }
    }

    private fun write(data: ByteArray, offset: Int, amount: Int) {
        lock.withLock {
            val fp = ensureInitialized()

            // only really write if we have a positive amount here.
            if (amount > 0) {
                // write the data
                seek(fp, written)
                fp.write(data, offset, amount)
                written += amount

                // update the metadata field
                seek(fp, totalSizeValue + 4)
                fp.writeInt(written)

                // sync the write to disk
                fp.fd.sync()
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
        fp.seek(pos.toLong())
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
        return written >= totalSizeValue
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
            // layout
            // < bytes:data > | <int:checksum> | <int:written>

            // get the length of the file and the checksum to test if we just created it,
            // or if it already contains data.
            val length = fp.length()

            val fileIsValid = length >= 8 && run {
                // seek 8 bytes before the end of the file to read metadata
                fp.seek(length - 8)
                fp.readInt() == expectedChecksum
            }

            if (fileIsValid) {
                logger.debug { "Found already cached file, loading metadata." }

                // read the total size from the file now.
                // We've previously read the first four bytes (checksum).
                totalSizeValue = length.toInt() - 8
                written = fp.readInt()

                if (written > totalSizeValue) {
                    // okay, someone fucked up! :/
                    logToCrashlytics(IOException("written=$written greater than totalSize=$totalSizeValue"))

                    // invalidate the file and try again.
                    fp.setLength(0)
                    fp.close()

                    this.fp = null

                    return initialize()
                }

            } else {
                logger.debug { "Entry is new, no data is previously cached." }

                // we can not have written anything yet.
                written = 0

                // start caching in the background now. This also resolves
                // the total size of the file
                totalSizeValue = resumeCaching()

                // re-size the file and write header at the end of the file
                fp.setLength(totalSizeValue.toLong())
                fp.writeInt(expectedChecksum)
                fp.writeInt(written)
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
        this.totalSizeValue = 0

        this.cacheWriter = null
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet and currently not caching.
     */
    private fun ensureCaching() {
        if (cacheWriter == null && !fullyCached()) {
            logger.debug { "Caching will start on entry ${this}" }
            resumeCaching()
        }
    }

    private fun resumeCaching(): Int {
        lock.withLock {
            // start a new cache writer if required.
            val writer = cacheWriter ?: run {
                val writer = CacheWriter()
                this.cacheWriter = writer

                // start the caching thread in background
                val offset = written
                doInBackground { writer.resumeCaching(offset) }

                writer
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

                val doTracking = !AndroidUtility.isOnMobile(context)

                logger.debug { "Resume caching for ${this} starting at $offset" }

                val connectStartTime = System.currentTimeMillis()
                val response = httpClient.newCall(request).execute()

                try {
                    val body = response.body()
                            ?: throw IllegalStateException("no body in media response")

                    when {
                        response.code() == 200 -> written = 0
                        response.code() == 403 -> throw IOException("Not allowed to read file, are you on a public wifi?")
                        response.code() == 404 -> throw FileNotFoundException("File not found at " + response.request().url())
                        response.code() != 206 -> throw IOException("Expected status code 2xx, got " + response.code())
                        body.contentLength() < 0L -> throw IOException("Content length not defined.")
                    }

                    size.setValue(body.contentLength().toInt())

                } catch (err: Exception) {
                    response.close()
                    throw err
                }

                val connectTime = System.currentTimeMillis() - connectStartTime

                // read the response
                val transferStartTime = System.currentTimeMillis()
                val bytesTransferred = writeResponseToEntry(response)

                // and track the duration and as such the speed.
                if (doTracking && bytesTransferred > 64 * 1024) {
                    val typeTag = if (uri.toString().endsWith(".mp4")) "media:video" else "media:image"

                    val bytesPerSecond = bytesTransferred * 1000L / (System.currentTimeMillis() - transferStartTime)
                    Stats().histogram("net.speed", bytesPerSecond, typeTag)
                    Stats().histogram("net.connect", connectTime, typeTag)
                    Stats().histogram("net.transferred", bytesTransferred, typeTag)
                }


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
        private fun writeResponseToEntry(response: Response): Long {
            var written = 0L

            response.body()?.use { body ->
                body.byteStream().use { stream ->
                    readStream(stream) { buffer, byteCount ->
                        lock.withLock {
                            if (canceled) {
                                logger.info { "Caching canceled, stopping now." }
                                return written
                            }

                            if (fp == null) {
                                logger.warn { "Error during caching, the file-handle went away: ${this}" }
                                return written
                            }

                            debug {
                                if (written > 1024 * 1024)
                                    throw EOFException("DEBUG Simulate network loss")
                            }

                            write(buffer, 0, byteCount)

                            written += byteCount
                        }
                    }

                    lock.withLock {
                        fp?.let { promoteFullyCached(it) }
                    }
                }

            }

            return written
        }
    }

    private fun promoteFullyCached(fp: RandomAccessFile) {
        // remove metadata from end of the file
        fp.setLength(totalSizeValue.toLong())
        fp.fd.sync()

        // replace with fully cached file
        val promoted = File(file.path + ".ok")
        file.renameTo(promoted)

        // replace this file with the delegate
        delegate = FileEntry(promoted)
    }

    /**
     * Increment the refCount
     */
    fun incrementRefCount(): CacheEntry {
        refCount.incrementAndGet()
        return this
    }

    override val totalSize: Int
        get() {
            // prefer the delegate if possible
            delegate?.let { return it.totalSize }

            ensureInitialized()
            return totalSizeValue
        }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    override fun close() {
        // delegate if needed
        delegate?.close()

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

    override fun inputStreamAt(offset: Int): InputStream {
        // serve directly from file if possible
        delegate?.let { return it.inputStreamAt(offset) }

        // update the time stamp if the cache file already exists.
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            logger.warn { "Could not update timestamp on $file" }
        }

        return EntryInputStream(incrementRefCount(), offset)
    }

    override val fractionCached: Float get() {
        delegate?.let { return it.fractionCached }

        return if (totalSizeValue > 0) {
            written / totalSizeValue.toFloat()
        } else {
            -1f
        }
    }

    protected fun finalize() {
        if (this.fp != null) {
            logger.warn { "Entry finalized, but file was not closed." }

            Stats().increment("cache.finalize.needed")
            fp.closeQuietly()

            reset()
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
            return "Entry(written=$written, totalSize=${totalSizeValue.takeIf { it > 0 }}, " +
                    "caching=${cacheWriter != null}, refCount=${refCount.get()}, " +
                    "fullyCached=${fullyCached()}, uri=$uri)"
        }
    }

    private class EntryInputStream(private val entry: CacheEntry, private var position: Int) : InputStream() {
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
}
