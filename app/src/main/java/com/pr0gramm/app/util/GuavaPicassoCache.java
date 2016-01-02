package com.pr0gramm.app.util;

import android.graphics.Bitmap;
import android.support.v4.graphics.BitmapCompat;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.squareup.picasso.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.google.common.collect.Iterables.filter;

/**
 * This is a better cache for {@link com.squareup.picasso.Picasso}.
 * Firstly, it uses {@link java.lang.ref.WeakReference}s for the values and secondly,
 * it only caches small images (for the feed).
 * <p>
 * This should prevent further out of memory errors.
 */
public class GuavaPicassoCache implements Cache {
    private static final Logger logger = LoggerFactory.getLogger("GuavaPicassoCache");
    private static final long MAX_CACHE_ITEM_SIZE = 128 * 128 * 4;

    private final com.google.common.cache.Cache<String, Bitmap> cache;
    private final int maxSize;

    public GuavaPicassoCache(int maxSize) {
        logger.info("Initializing cache with about " + maxSize / (1024 * 1024) + "mb");

        this.maxSize = maxSize;
        cache = CacheBuilder.<String, Bitmap>newBuilder()
                .softValues()
                .weigher((String key, Bitmap bitmap) -> bitmapByteCount(bitmap))
                .maximumWeight(maxSize)
                .recordStats()
                .build();
    }

    private int bitmapByteCount(Bitmap bitmap) {
        return BitmapCompat.getAllocationByteCount(bitmap);
    }

    @Override
    public Bitmap get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void set(String key, Bitmap bitmap) {
        if (bitmapByteCount(bitmap) <= MAX_CACHE_ITEM_SIZE) {
            cache.put(key, bitmap);
        }

        // logger.info("Stats: " + cache.stats());
    }

    @Override
    public int size() {
        int size = 0;
        for (Bitmap bitmap : cache.asMap().values())
            size += bitmapByteCount(bitmap);

        return size;
    }

    @Override
    public int maxSize() {
        return maxSize;
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    @Override
    public void clearKeyUri(String keyPrefix) {
        List<String> keys = ImmutableList.copyOf(cache.asMap().keySet());
        Iterable<String> matchingKeys = filter(keys, key -> key.startsWith(keyPrefix));
        cache.invalidateAll(matchingKeys);
    }

    public static GuavaPicassoCache defaultSizedGuavaCache() {
        int maxMemory = Math.max(1024 * 1024, (int) (Runtime.getRuntime().maxMemory() / 20L));
        return new GuavaPicassoCache(maxMemory);
    }
}
