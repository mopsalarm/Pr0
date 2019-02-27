package com.pr0gramm.app.io

import android.net.Uri
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.logToCrashlytics
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A entry that is hold by the [Cache].
 */
internal class CacheEntry(
        private val httpClient: OkHttpClient,
        private val partialCached: File,
        private val fullyCached: File,
        private val uri: Uri) : Cache.Entry {

    private val logger = if (BuildConfig.DEBUG) Logger("CacheEntry(${uri.lastPathSegment})") else Logger("CacheEntry")

    // lock to guard all io operations
    private val lock = ReentrantLock()

    // number of bytes currently written to disk. must only be modified
    // if holding the lock.
    private var written: Int = 0
    private val writtenUpdated = lock.newCondition()

    private val refCount = AtomicInteger()
    private var fp: RandomAccessFile? = null

    private var fileCacher: Cacher? = null

    // delegate all calls to this delegate if set
    private var delegate: FileEntry? = null

    private var totalSizeValue: Int = -1

    fun read(pos: Int, data: ByteArray, offset: Int, amount: Int): Int {
        // Always succeed when reading 0 bytes.
        if (amount == 0) {
            return 0
        }

        lock.withLock {
            val fp = ensureOpen()

            // if we are at the end of the file, we need to signal that
            if (pos >= totalSizeValue) {
                return -1
            }

            // check how much we can actually read at most!
            val amountToRead = Math.min(pos + amount, totalSizeValue) - pos

            // wait for the data to be there
            expectCached(pos + amountToRead)

            // now try to read the bytes we requested
            fp.seek(pos.toLong())
            val byteCount = read(fp, data, offset, amountToRead)

            // check if we got as many bytes as we wanted to.
            if (byteCount != amountToRead) {
                logToCrashlytics(EOFException(
                        "Expected to read $amountToRead bytes at $pos, but got only $byteCount. Cache entry: ${this}"))
            }

            return byteCount
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

    private fun ensureOpen(): RandomAccessFile {
        lock.withLock {
            // we are initialized if we already have a opened file.
            // just return that one.
            val fp = fp
            if (fp != null) {
                return fp
            }

            logger.debug { "Entry needs to be initialized: ${this}" }
            return open()
        }
    }

    /**
     * Returns true, if the entry is fully cached.
     * You need to hold the lock to call this method.
     */
    private fun fullyCached(): Boolean {
        return delegate != null && written >= totalSizeValue
    }

    /**
     * Will be called if we need to initialize the file.
     * If this is called, we can expect the entry to hold its own lock.
     */
    private fun open(): RandomAccessFile {
        lock.requireLocked()

        // ensure that the parent directory exists.
        val parentFile = partialCached.parentFile
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                logger.warn { "Could not create parent directory." }
            }
        }

        if (fullyCached.exists()) {
            logger.debug { "open() called even if file was already fully cached" }
            // try to open fullyCached file in read-only mode first.
            // the file was already cached but someone still wants to use our
            // old entry.
            //
            // also we can now set the delegate if not already done.
            val fp = RandomAccessFile(fullyCached, "r")

            this.fp = fp
            this.written = fp.length().toInt()
            this.totalSizeValue = fp.length().toInt()

            if (delegate == null) {
                logger.debug { "delegate not yet set, setting it now" }
                delegate = FileEntry(fullyCached)
            }

            return fp
        }

        // open the file in read/write mode, creating it if it did not exist.
        val fp = RandomAccessFile(partialCached, "rwd")
        this.fp = fp

        try {
            logger.debug { "partialCached was opened by initialize()" }

            // consider the already cached bytes
            written = fp.length().toInt()

            // start caching in background
            totalSizeValue = resumeCaching()

            if (written > totalSizeValue) {
                // okay, someone fucked up! :/
                logToCrashlytics(IOException("written=$written greater than totalSize=$totalSizeValue"))

                // invalidate the file and try again.
                fp.setLength(0)
                fp.close()

                this.fp = null

                logger.debug { "Opening file again." }
                return open()
            }

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
        this.fp?.closeQuietly()
        this.fp = null
        this.written = 0
        this.totalSizeValue = -1
        this.fileCacher = null
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet and currently not caching.
     */
    private fun ensureCaching() {
        if (fileCacher == null && !fullyCached()) {
            logger.debug { "Caching will start on entry ${this}" }
            resumeCaching()
        }
    }

    private fun resumeCaching(): Int {
        lock.withLock {
            // start a new cache writer if required.
            val cacher = fileCacher ?: Cacher().also { cacher ->
                this.fileCacher = cacher

                logger.debug { "Resume caching in a background thread" }

                // resume caching in the background.
                val offset = written
                doInBackground { cacher.resumeCaching(offset) }
            }

            return try {
                logger.debug { "Wait for totalSize future to be available" }
                cacher.totalSize.get()
            } catch(err: ExecutionException) {
                // throw the real error, not the wrapped one.
                throw err.cause ?: err
            }
        }
    }

    private inner class Cacher {
        val canceled get() = fileCacher !== this

        val totalSize = SettableFuture<Int>()

        /**
         * This method is called from the caching thread once caching stops.
         */
        private fun cachingStopped() {
            logger.debug { "Caching stopped on entry ${this}" }
            lock.withLock {
                if (!canceled) {
                    close()
                    fileCacher = null
                }

                // If there are any readers, we need to notify them, so caching will be
                // re-started if needed
                writtenUpdated.signalAll()
            }
        }

        fun resumeCaching(offset: Int) {
            incrementRefCount()

            try {
                logger.debug { "Resume caching for ${this} starting at $offset" }

                val request = Request.Builder()
                        .url(uri.toString())
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .apply {
                            if (offset > 0) {
                                header("Range", "bytes=$offset-")
                            }
                        }
                        .build()

                // do the call synchronously
                val response = httpClient.newCall(request).execute()

                val totalSize = try {
                    logger.debug { "Response is $response" }
                    val body = response.body()
                            ?: throw IllegalStateException("no body in media response")

                    when {
                        response.code() == 200 -> {
                            written = 0
                            body.contentLength().toInt()
                        }

                        response.code() == 206 -> {
                            val range = response.header("Content-Range")
                                    ?: throw IOException("Expected Content-Range header")

                            logger.debug { "Got Content-Range header with $range" }
                            parseRangeHeaderTotalSize(range)
                        }

                        response.code() == 403 ->
                            throw IOException("Not allowed to read file, are you on a public wifi?")

                        response.code() == 404 ->
                            throw FileNotFoundException("File not found at " + response.request().url())

                        else -> throw IOException("Request for ${request.url()} failed with status ${response.code()}")
                    }

                    // we now know the size of the full file


                } catch (err: Exception) {
                    logger.debug { "Closing the response because of an error." }
                    response.closeQuietly()

                    throw err
                }

                // we now know the size, publish it to waiting consumers
                this.totalSize.setValue(totalSize)

                logger.debug { "Writing response to cache file" }
                writeResponseToEntry(response)

                // check if we need can now promote the cached file to the target file
                promoteFullyCached(totalSize)

            } catch (err: Exception) {
                logger.error { "Error in caching thread (${err}" }
                this.totalSize.setError(err)

            } finally {
                cachingStopped()
            }
        }

        /**
         * Writes the response to the file. If [fp] disappears, we log a warning
         * and then we just return.
         */
        private fun writeResponseToEntry(response: Response) {
            val lockExpireTime = Instant.now().plus(1, TimeUnit.SECONDS)

            response.body()?.use { body ->
                body.byteStream().use { stream ->
                    var localWritten = 0

                    readStream(stream, bufferSize = 64 * 1024) { buffer, byteCount ->
                        lock.withLock {
                            logger.debug { "Got $byteCount new bytes from cache." }

                            val fp = fp
                            if (fp == null) {
                                logger.warn { "Error during caching, the file-handle went away: ${this}" }
                                return
                            }

                            write(fp, buffer, 0, byteCount)

                            if (canceled) {
                                logger.info { "Caching canceled, stopping now." }
                                return
                            }

                            debug {
                                localWritten += byteCount
                                if (localWritten > 1024 * 1024)
                                    throw EOFException("DEBUG Simulate network loss")
                            }

                            if (refCount.toInt() == 1 && lockExpireTime.isInPast) {
                                throw TimeoutException("No one holds a reference to the cache entry, stop caching now.")
                            }
                        }
                    }
                }
            }
        }

        private fun write(fp: RandomAccessFile, data: ByteArray, offset: Int, byteCount: Int) {
            lock.requireLocked()

            if (byteCount > 0) {
                logger.debug { "Writing $byteCount of data at byte index $written" }

                // write the data to the right place
                fp.seek(written.toLong())
                fp.write(data, offset, byteCount)

                // and increase the write pointer
                written += byteCount
            }

            // tell any readers about the new data.
            writtenUpdated.signalAll()
        }

        private fun promoteFullyCached(expectedSize: Int) {
            // sync to disk if needed
            fp?.fd?.sync()

            lock.withLock {
                fullyCached.length().let { fullyCachedSize ->
                    if (fullyCachedSize > 0 && fullyCachedSize != expectedSize.toLong()) {
                        logger.debug { "Deleting fullyCached file with wrong size." }
                        fullyCached.delete()
                    }
                }

                if (partialCached.length() != expectedSize.toLong()) {
                    throw IllegalStateException("partialCached file has unexpected size, expected $expectedSize")
                }

                // rename to fully cached file. All read operations can now work directly
                // from that file.
                partialCached.renameTo(fullyCached)

                // delegate to new entry from now on.
                delegate = FileEntry(fullyCached)
            }
        }

        override fun toString(): String {
            return "Cacher()"
        }
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
            file?.let { fullyCached ->
                return fullyCached.length().toInt()
            }

            ensureOpen()
            return totalSizeValue
        }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    override fun close() {
        lock.withLock {
            if (this.refCount.decrementAndGet() == 0 && this.fp != null) {
                logger.debug { "Closing cache file for entry ${this} now." }
                reset()
            }
        }
    }

    override val file: File? get() = delegate?.file

    override fun inputStreamAt(offset: Int): InputStream {
        // serve directly from file if possible
        delegate?.let { return it.inputStreamAt(offset) }

        lock.withLock {
            // update the time stamp if the cache file already exists.
            if (!partialCached.updateTimestamp()) {
                logger.warn { "Could not update timestamp on $partialCached" }
            }

            return EntryInputStream(incrementRefCount(), offset)
        }
    }

    override val fractionCached: Float get() {
        delegate?.let { return it.fractionCached }

        return if (totalSizeValue > 0) {
            written / totalSizeValue.toFloat()
        } else {
            -1f
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
        return lock.withTryLock {
            "Entry(written=$written, totalSize=${totalSizeValue.takeIf { it > 0 }}, " +
                    "caching=${fileCacher != null}, refCount=${refCount.get()}, " +
                    "fullyCached=${fullyCached()}, uri=$uri)"
        }
    }

    private class EntryInputStream(private val entry: CacheEntry, private var position: Int) : InputStream() {
        private val closed = AtomicBoolean()
        private var mark: Int = 0

        override fun read(): Int {
            throw UnsupportedOperationException("read() not implemented due to performance.")
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
            if (closed.compareAndSet(false, true)) {
                entry.close()
            }
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

    private fun parseRangeHeaderTotalSize(header: String): Int {
        return header.takeLastWhile { it != '/' }.toInt()
    }
}

private inline fun <T> ReentrantLock.withTryLock(block: () -> T): T {
    val locked = tryLock()
    return try {
        block()
    } finally {
        if (locked) {
            unlock()
        }
    }
}

private fun ReentrantLock.requireLocked() {
    debug {
        if (!isHeldByCurrentThread) {
            throw IllegalStateException("Current thread does not hold the lock.")
        }
    }
}
