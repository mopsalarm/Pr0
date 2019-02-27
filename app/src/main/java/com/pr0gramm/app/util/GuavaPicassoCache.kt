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
class GuavaPicassoCache private constructor(maxSize: Int) : Cache {
    private val logger = Logger("GuavaPicassoCache")

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
        if (BitmapCompat.getAllocationByteCount(bitmap) <= MAX_CACHE_ITEM_SIZE) {
            cache.put(key, bitmap)
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
        private const val MAX_CACHE_ITEM_SIZE = (128 * 128 * 4).toLong()

        fun defaultSizedGuavaCache(): GuavaPicassoCache {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 20L).toInt()
                    .coerceIn(minimumValue = 2 * 1024 * 1024, maximumValue = 8 * 1024 * 1024)

            return GuavaPicassoCache(maxMemory)
        }
    }
}
