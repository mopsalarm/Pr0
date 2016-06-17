package com.pr0gramm.app.io;

import java.io.IOException;
import java.io.InputStream;

/**
 */
public interface InputStreamCache {
    InputStream get() throws IOException;

    /**
     * Closes and invalidates the cache.
     */
    void close() throws IOException;

    /**
     * Returns the number of bytes that are already buffered.
     */
    int cacheSize();
}
