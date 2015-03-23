package com.pr0gramm.app.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 */
public interface CacheEntryPersister {
    /**
     * Persists a cache entry into the given output stream
     */
    void persist(CacheEntry cacheEntry, OutputStream output) throws IOException;

    /**
     * Parses a cache entry from the given input stream
     *
     * @return The parsed cache entry
     */
    CacheEntry load(InputStream stream) throws IOException;
}
