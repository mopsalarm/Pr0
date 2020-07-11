package com.pr0gramm.app.util

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.core.graphics.BitmapCompat
import com.pr0gramm.app.Logger
import com.squareup.picasso.Cache

/**
 * This is a better cache for [com.squareup.picasso3.Picasso].
 * It caches only small images (for the feed).
 *
 * This should prevent further out of memory errors.
 */
class LruPicassoCache private constructor(maxSize: Int) : Cache {
    private val logger = Logger("LruPicassoCache")

    private val cache = object : LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = BitmapCompat.getAllocationByteCount(value)
    }

    init {
        logger.info { "Initializing cache with about ${maxSize / (1024 * 1024)}mb" }
    }

    override fun get(key: String): Bitmap? {
        return cache.get(key)
    }

    override fun set(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)

        debugOnly {
            val bitmapCount = cache.snapshot().size
            logger.debug {
                "Added bitmap ${bitmap.width}x${bitmap.height} image to cache. " +
                        "There are now $bitmapCount elements cached at ${cache.size() / 1024}kb of ${cache.maxSize() / 1024}kb"
            }
        }
    }

    override fun size(): Int {
        return cache.size()
    }

    override fun maxSize(): Int {
        return cache.maxSize()
    }

    override fun clear() {
        cache.evictAll()
    }

    override fun clearKeyUri(keyPrefix: String) {
        cache.snapshot().keys.filter { it.startsWith(keyPrefix) }.forEach { cache.remove(it) }
    }

    companion object {
        fun defaultSizedCache(): LruPicassoCache {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 10L).toInt()
                    .coerceIn(minimumValue = 4 * 1024 * 1024, maximumValue = 8 * 1024 * 1024)

            return LruPicassoCache(maxMemory)
        }
    }
}
