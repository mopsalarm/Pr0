package com.pr0gramm.app.mpeg;

import android.support.annotation.NonNull;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 */
public class InputStreamCache {
    private final List<ByteSource> cache = new ArrayList<>();
    private final InputStream backend;
    private boolean closed;

    public InputStreamCache(InputStream backend) {
        this.backend = backend;
    }

    private void checkIfOpen() {
        if (closed) {
            throw new IllegalStateException("input stream is closed.");
        }
    }

    public InputStream get() throws IOException {
        if (closed) {
            return ByteSource.concat(cache).openStream();
        } else {
            return new CachingInputStream();
        }
    }

    private class CachingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Never called anyways");
        }

        @Override
        public int read(@NonNull byte[] buffer) throws IOException {
            return this.read(buffer, 0, buffer.length);
        }

        @Override
        public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
            checkIfOpen();
            int result = backend.read(buffer, byteOffset, byteCount);
            if (result > 0) {
                cache.add(ByteSource.wrap(Arrays.copyOfRange(buffer, byteOffset, byteOffset + result)));
            }

            return result;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            backend.close();
        }
    }
}
