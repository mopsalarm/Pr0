package com.pr0gramm.app;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.squareup.picasso.Cache;

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
    private static final long MAX_CACHE_ITEM_SIZE = 128 * 128 * 4;

    private final com.google.common.cache.Cache<String, Bitmap> cache;
    private final int maxSize;

    public GuavaPicassoCache(int maxSize) {
        Log.i("PicassoCache", "Initializing cache with about " + maxSize / (1024 * 1024) + "mb");

        this.maxSize = maxSize;
        cache = CacheBuilder.<String, Bitmap>newBuilder()
                .softValues()
                .weigher((String key, Bitmap bitmap) -> bitmap.getByteCount())
                .maximumWeight(maxSize)
                .recordStats()
                .build();
    }

    @Override
    public Bitmap get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void set(String key, Bitmap bitmap) {
        if (bitmap.getByteCount() <= MAX_CACHE_ITEM_SIZE) {
            cache.put(key, bitmap);
        }

        // Log.i("PicassoCache", "Stats: " + cache.stats());
    }

    @Override
    public int size() {
        int size = 0;
        for (Bitmap bitmap : cache.asMap().values())
            size += bitmap.getByteCount();

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
        // go for about 10% of the runtimes memory
        int maxMemory = (int) Runtime.getRuntime().maxMemory() / 10;
        return new GuavaPicassoCache(maxMemory);
    }
}
