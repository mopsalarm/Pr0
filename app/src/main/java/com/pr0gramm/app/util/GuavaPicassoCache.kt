package com.pr0gramm.app.util

import android.graphics.Bitmap
import androidx.core.graphics.BitmapCompat
import com.squareup.picasso.Cache
import kotlin.concurrent.timer

/**
 * This is a better cache for [com.squareup.picasso.Picasso].
 * It caches only small images (for the feed).
 *
 * This should prevent further out of memory errors.
 */
class GuavaPicassoCache private constructor(maxSize: Int) : Cache {
    private val cache = object : androidx.collection.LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = bitmapByteCount(value)
    }

    init {
        logger.info("Initializing cache with about " + maxSize / (1024 * 1024) + "mb")

        debug {
            timer(period = 10000) {
                logger.info("Cache stats: {}", cache.toString())
            }
        }
    }

    private fun bitmapByteCount(bitmap: Bitmap): Int {
        return BitmapCompat.getAllocationByteCount(bitmap)
    }

    override fun get(key: String): Bitmap? {
        return cache.get(key)
    }

    override fun set(key: String, bitmap: Bitmap) {
        if (bitmapByteCount(bitmap) <= MAX_CACHE_ITEM_SIZE) {
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
        private val logger = logger("GuavaPicassoCache")
        private const val MAX_CACHE_ITEM_SIZE = (128 * 128 * 4).toLong()

        @JvmStatic
        fun defaultSizedGuavaCache(): GuavaPicassoCache {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 20L).toInt()
                    .coerceIn(minimumValue = 2 * 1024 * 1024, maximumValue = 6 * 1024 * 1024)

            return GuavaPicassoCache(maxMemory)
        }
    }
}
