package com.pr0gramm.app.cache;

/**
 * Listener that is informed after a write on a cache entry.
 */
public interface OnWriteListener {
    /**
     * Gets called after the given cache entry was written.
     * It is important, that you never throw an exception from this method.
     *
     * @param cacheEntry The cache entry that was written
     * @param block      The block that was modified.
     */
    void onWrite(CacheEntry cacheEntry, int block);
}
