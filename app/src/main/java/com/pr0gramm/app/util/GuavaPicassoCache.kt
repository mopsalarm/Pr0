package com.pr0gramm.app.util

import android.graphics.Bitmap
import android.support.v4.graphics.BitmapCompat
import android.support.v4.util.LruCache
import com.squareup.picasso.Cache
import org.slf4j.LoggerFactory

/**
 * This is a better cache for [com.squareup.picasso.Picasso].
 * It caches only small images (for the feed).
 *
 * This should prevent further out of memory errors.
 */
class GuavaPicassoCache private constructor(maxSize: Int) : Cache {
    private val cache = object : LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = bitmapByteCount(value)
    }

    init {
        logger.info("Initializing cache with about " + maxSize / (1024 * 1024) + "mb")
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
        private val logger = LoggerFactory.getLogger("GuavaPicassoCache")
        private const val MAX_CACHE_ITEM_SIZE = (128 * 128 * 4).toLong()

        @JvmStatic
        fun defaultSizedGuavaCache(): GuavaPicassoCache {
            val maxMemory = Math.max(2 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 20L).toInt())
            return GuavaPicassoCache(maxMemory)
        }
    }
}
