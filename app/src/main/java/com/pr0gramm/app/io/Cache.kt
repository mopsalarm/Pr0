package com.pr0gramm.app.io

import android.app.Application
import android.net.Uri
import com.pr0gramm.app.Stats
import com.pr0gramm.app.util.AndroidUtility.toFile
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.doInBackground
import okhttp3.*
import okio.Okio
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
class Cache(private val context: Application, private val httpClient: OkHttpClient) {
    private val MEGA = (1024 * 1024).toLong()

    private val logger = Logger("Cache")
    private val root: File = File(context.cacheDir, "mediacache")

    private val lock = Any()
    private val cache = HashMap<String, Entry>()

    init {
        // schedule periodic cache clean up.
        Observable.interval(10, 60, TimeUnit.SECONDS).subscribe {
            doInBackground {
                cleanupCache()
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
        logger.debug { "Creating a new cache entry for uri $uri" }

        // just open the file directly if it is local.
        if (uri.scheme == "file") {
            return FileEntry(toFile(uri))
        }

        val cacheFile = cacheFileFor(uri)
        return CacheEntry(context, httpClient, cacheFile, uri)
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

        val files = root.listFiles().sortedByDescending { it.lastModified() }

        // The space already in use by the cache
        val usedCacheSpace = files.fold(0L) { acc, file -> acc + file.length() }

        // The amount that can be used by apps
        val usableSpace = root.usableSpace - 1024 * MEGA

        // Ignoring what the cache already uses, this is the amount of space that
        // is available to us.
        val availableSpace = usedCacheSpace + usableSpace

        // Now put it in sane limits.
        val maxCacheSize = availableSpace.coerceIn(256 * MEGA, 2048 * MEGA)

        logger.debug { "Doing cache cleanup, maxCacheSize=${formatSpace(maxCacheSize)}, found ${files.size} files using ${formatSpace(usedCacheSpace)}" }

        var usedSpace: Long = 0
        for (file in files) {
            usedSpace += file.length()

            if (usedSpace > maxCacheSize) {
                forgetEntryForFile(file)
            }
        }

        // do some tracking of cache sizes
        Stats().histogram("cache.maxSize", maxCacheSize)
        Stats().histogram("cache.usedSize", usedSpace)
        Stats().histogram("cache.usage", usedSpace.toDouble() / maxCacheSize)
    }

    /**
     * Removes the cached entry with the given filename.
     */
    private fun forgetEntryForFile(file: File) {
        logger.debug { "Remove old cache file $file" }
        if (!file.delete()) {
            logger.warn { "Could not delete cached file $file" }
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
        return try {
            lhs.canonicalPath == rhs.canonicalPath
        } catch(err: IOException) {
            logger.warn { "Could not check if files are the same: $lhs, $rhs, err: $err" }
            false
        }
    }

    private fun formatSpace(space: Long): String {
        return "%1.2fmb".format(space.toFloat() / (1024f * 1024f))
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
