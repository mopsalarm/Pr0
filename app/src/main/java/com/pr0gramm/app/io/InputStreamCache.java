package com.pr0gramm.app.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by oliver on 20.03.16.
 */
public interface InputStreamCache {
    InputStream get() throws IOException;

    /**
     * Closes and invalidates the cache.
     */
    void close() throws IOException;
}
