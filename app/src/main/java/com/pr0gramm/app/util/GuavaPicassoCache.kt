package com.pr0gramm.app.util

import android.graphics.Bitmap
import android.support.v4.graphics.BitmapCompat
import com.google.common.cache.CacheBuilder
import com.squareup.picasso.Cache
import org.slf4j.LoggerFactory

/**
 * This is a better cache for [com.squareup.picasso.Picasso].
 * Firstly, it uses [java.lang.ref.WeakReference]s for the values and secondly,
 * it only caches small images (for the feed).
 *
 *
 * This should prevent further out of memory errors.
 */
class GuavaPicassoCache private constructor(private val maxSize: Int) : Cache {
    private val cache = CacheBuilder.newBuilder()
            .weigher { _: String, bitmap: Bitmap -> bitmapByteCount(bitmap) }
            .maximumWeight(maxSize.toLong())
            .build<String, Bitmap>()

    init {
        logger.info("Initializing cache with about " + maxSize / (1024 * 1024) + "mb")
    }

    private fun bitmapByteCount(bitmap: Bitmap): Int {
        return BitmapCompat.getAllocationByteCount(bitmap)
    }

    override fun get(key: String): Bitmap? {
        return cache.getIfPresent(key)
    }

    override fun set(key: String, bitmap: Bitmap) {
        if (bitmapByteCount(bitmap) <= MAX_CACHE_ITEM_SIZE) {
            cache.put(key, bitmap)
        }
    }

    override fun size(): Int {
        return cache.asMap().values.sumBy { bitmapByteCount(it) }
    }

    override fun maxSize(): Int {
        return maxSize
    }

    override fun clear() {
        cache.invalidateAll()
        cache.cleanUp()
    }

    override fun clearKeyUri(keyPrefix: String) {
        val keys = cache.asMap().keys.map { it.startsWith(keyPrefix) }
        cache.invalidateAll(keys)
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
