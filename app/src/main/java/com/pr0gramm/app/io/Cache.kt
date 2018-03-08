package com.pr0gramm.app.io

import android.content.Context
import android.net.Uri
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.util.AndroidUtility.toFile
import com.pr0gramm.app.util.BackgroundScheduler
import okhttp3.*
import okio.Okio
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * A cache we can use for linear caching of http requests.
 */
class Cache(context: Context, private val httpClient: OkHttpClient) {
    private val MEGA = (1024 * 1024).toLong()

    private val logger = LoggerFactory.getLogger("Cache")
    private val root: File = File(context.cacheDir, "mediacache")

    private val lock = Any()
    private val cache = HashMap<String, Entry>()

    private val maxCacheSize: Long

    init {
        // schedule periodic cache clean up.
        Observable.interval(10, 60, TimeUnit.SECONDS, BackgroundScheduler.instance()).subscribe {
            try {
                cleanupCache()
            } catch(err: Exception) {
                logger.warn("Ignoring error during cache cleanup:", err)
            }
        }

        // print cache every few seconds for debugging
        if (BuildConfig.DEBUG) {
            Observable
                    .interval(5, TimeUnit.SECONDS, BackgroundScheduler.instance())
                    .subscribe { printCache() }
        }

        maxCacheSize = if (root.freeSpace > 1024 * MEGA) 256 * MEGA else 128 * MEGA
        logger.debug("Initialized cache with {}mb of space", maxCacheSize / MEGA.toDouble())
    }

    private fun printCache() {
        synchronized(lock) {
            logger.debug("Cache:")
            for (entry in cache.values) {
                logger.debug("  * {}", entry)
            }
        }
    }

    /**
     * Returns a cached or caching entry. You need to close your reference
     * once you are finish with it.
     */
    fun get(uri: Uri): Cache.Entry {
        val key = uri.toString()

        synchronized(lock) {
            val entry: Entry = cache.getOrPut(key) { createEntry(uri) }
            return refCountIfNeeded(entry)
        }
    }

    private fun refCountIfNeeded(entry: Entry): Entry {
        if (entry is CacheEntry) {
            entry.incrementRefCount()
        }

        return entry
    }

    private fun createEntry(uri: Uri): Entry {
        logger.debug("Creating a new cache entry for uri {}", uri)

        // just open the file directly if it is local.
        if (uri.scheme == "file") {
            return FileEntry(toFile(uri))
        }

        val cacheFile = cacheFileFor(uri)
        return CacheEntry(httpClient, cacheFile, uri)
    }

    /**
     * Retuns the cache file for the given uri.
     */
    private fun cacheFileFor(uri: Uri): File {
        val filename = uri.toString().replaceFirst("https?://".toRegex(), "").replace("[^a-zA-Z0-9.]+".toRegex(), "_")
        return File(root, filename)
    }

    /**
     * Checks for old files in the cache directory and removes the oldest files
     * if they exceed the maximum cache size.
     */
    private fun cleanupCache() {
        if (!root.exists()) {
            return
        }

        val files = root.listFiles().sortedByDescending(File::lastModified)
        logger.debug("Doing cache cleanup, found {} files", files.size)

        var totalSize: Long = 0
        for (file in files) {
            totalSize += file.length()

            if (totalSize > maxCacheSize) {
                forgetEntryForFile(file)
            }
        }

        logger.debug("Cache took {}mb", totalSize / (1024f * 1024f))
    }

    /**
     * Removes the cached entry with the given filename.
     */
    private fun forgetEntryForFile(file: File) {
        logger.debug("Remove old cache file {}", file)
        if (!file.delete()) {
            logger.warn("Could not delete cached file {}", file)
        }

        synchronized(lock) {
            for ((key, entry) in cache) {
                if (isSameFile(entry.file, file)) {
                    // remove the entry from our cache
                    cache.remove(key)
                    break
                }
            }
        }
    }

    private fun isSameFile(lhs: File, rhs: File): Boolean {
        try {
            return lhs.canonicalPath == rhs.canonicalPath
        } catch(err: IOException) {
            logger.warn(
                    "Could not check if files are the same: {}, {}, err: {}",
                    lhs, rhs, err.toString())

            return false
        }
    }

    interface Entry : Closeable {
        val totalSize: Int

        fun inputStreamAt(offset: Int): InputStream

        /**
         * Returns a value between 0 and 1 that specifies how much of this
         * entry is actually cached. Returns -1 if no estimate is currently available.
         */
        val fractionCached: Float

        val file: File

        override fun close()

        fun toResponse(request: Request, mediaType: MediaType? = null): Response {
            val body = ResponseBody.create(
                    mediaType, totalSize.toLong(),
                    Okio.buffer(Okio.source(inputStreamAt(0))))

            return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_0)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
        }
    }
}
