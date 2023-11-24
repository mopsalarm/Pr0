package com.pr0gramm.app.io

import android.app.Application
import android.net.Uri
import androidx.core.net.toFile
import com.pr0gramm.app.Duration.Companion.seconds
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Stats
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.isLocalFile
import com.pr0gramm.app.util.runEvery
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.io.Closeable
import java.io.File
import java.io.InputStream


/**
 * A cache we can use for linear caching of http requests.
 */
class Cache(private val context: Application, private val httpClient: OkHttpClient) {
    private val Long.mega: Long get() = this * 1024L * 1024L

    private val logger = Logger("Cache")
    private val root: File = File(context.cacheDir, "mediacache")

    private val lock = Any()
    private val cache = HashMap<String, Entry>()

    init {
        doInBackground {
            runEvery(seconds(60), initial = seconds(10)) {
                cleanupCache()
            }
        }
    }

    /**
     * Returns a cached or caching entry. You need to close your reference
     * once you are finish with it.
     */
    fun get(uri: Uri): Entry {
        val key = cacheKeyOf(uri)

        synchronized(lock) {
            logger.debug { "Looking up cache entry for $key" }

            var entry: Entry? = cache[key]
            if (entry?.valid != true) {
                entry = createEntry(uri)
                cache[key] = entry
            }

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

        // just open the file directly if it is already local.
        if (uri.isLocalFile) {
            logger.debug { "Uri is local, returning FileEntry(${uri.toFile()})" }
            return FileEntry(uri.toFile())
        }

        // derive the files basename from the url
        val basename = uri.toString()
                .replaceFirst("https?://".toRegex(), "")
                .replace("[^a-zA-Z0-9.]+".toRegex(), "_")

        // check if we have a fully cached file
        val fullyCached = File(root, "$basename.ok")
        if (fullyCached.exists() && fullyCached.length() > 0) {
            logger.debug { "Fully cached file exists, returning FileEntry(${fullyCached}" }
            return FileEntry(fullyCached)
        }

        logger.debug { "No cached file found for $uri, creating new CacheEntry." }

        // resume a previously cached file
        val partialCached = File(root, "$basename.part")
        return CacheEntry(httpClient, partialCached, fullyCached, uri)
    }

    /**
     * Checks for old files in the cache directory and removes the oldest files
     * if they exceed the maximum cache size.
     */
    private fun cleanupCache() {
        if (!root.exists()) {
            return
        }

        val filesInRoot = root.listFiles() ?: return

        // cache times to sort without someone modifying timestamps during sorting between
        val modificationTimes = filesInRoot.associate { Pair(it, it.lastModified()) }
        val files = modificationTimes.keys.sortedByDescending { modificationTimes[it] }

        // The space already in use by the cache
        val usedCacheSpace = files.fold(0L) { acc, file -> acc + file.length() }

        // The amount that can be used by apps
        val usableSpace = root.usableSpace - 1024L.mega

        // Ignoring what the cache already uses, this is the amount of space that
        // is available to us.
        val availableSpace = usedCacheSpace + usableSpace

        // Now put it in sane limits.
        val maxCacheSize = availableSpace.coerceIn(256L.mega, 512L.mega)

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
    }

    private fun formatSpace(space: Long): String {
        return "%1.2fmb".format(space.toFloat() / (1024f * 1024f))
    }

    private fun cacheKeyOf(uri: Uri): String {
        return uri.toString().replaceFirst("http://", "https://")
    }

    interface Entry : Closeable {
        /**
         * Total size of this cache entry.
         */
        val totalSize: Int

        /**
         * Returns a value between 0 and 1 that specifies how much of this
         * entry is actually cached. Returns -1 if no estimate is currently available.
         */
        val fractionCached: Float

        /**
         * Not null if this cache entry is backed by a file.
         */
        val file: File?

        /**
         * Check if the entry is still valid
         */
        val valid: Boolean get() = file?.exists() == true

        /**
         * An input stream that starts at the given place in the file.
         */
        fun inputStreamAt(offset: Int): InputStream

        /**
         * Closes the reference to the entry. Must be called after getting
         * a Entry instance using Cache#get.
         */
        override fun close()

        fun toResponse(request: Request, mediaType: MediaType? = null): Response {
            val body = inputStreamAt(0).source().buffer().asResponseBody(mediaType, totalSize.toLong())

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
