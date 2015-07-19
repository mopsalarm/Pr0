package com.pr0gramm.app.mpeg;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 */
public class InputStreamCache {
    private final File directory;
    private InputStream backend;
    private RandomAccessFile cache;

    public InputStreamCache(Context context, InputStream backend) {
        this.directory = context.getCacheDir();
        this.backend = backend;
    }

    private RandomAccessFile newCacheFile() throws IOException {
        File file = File.createTempFile("video", "cache", directory);
        try {
            return new RandomAccessFile(file, "rw");

        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private boolean backendIsClosed() {
        return backend == null;
    }

    private void checkIfOpen() {
        if (backendIsClosed()) {
            throw new IllegalStateException("input stream is closed.");
        }
    }

    public InputStream get() throws IOException {
        if (backendIsClosed()) {
            // reset the cache and return a new input stream.
            cache.seek(0);
            return new FileInputStream(cache.getFD());
        } else {
            cache = newCacheFile();
            return new CachingInputStream();
        }
    }

    /**
     * Closes and invalidates the cache.
     */
    public void close() throws IOException {
        InputStream backend = this.backend;
        if (backend != null)
            this.backend.close();

        if (cache != null)
            cache.close();
    }

    private class CachingInputStream extends InputStream {
        private ByteBuffer current;

        private void freeze() throws IOException {
            ByteBuffer buffer = current;
            if (buffer != null && buffer.position() > 0) {
                buffer.flip();

                cache.write(buffer.array(), 0, buffer.remaining());

                // clear the buffer
                current.clear();
            }
        }

        private ByteBuffer get(int space) throws IOException {
            if (current != null && current.remaining() < space) {
                freeze();
            }

            if (current == null || current.remaining() < space) {
                current = ByteBuffer.allocate(Math.max(space, 64 * 1024));
            }

            return current;
        }

        @Override
        public int read() throws IOException {
            checkIfOpen();

            int result = backend.read();
            if (result >= 0) {
                ByteBuffer buffer = get(1);
                buffer.put((byte) result);
            }

            return result;
        }

        @Override
        public int read(@NonNull byte[] bytes, int byteOffset, int byteCount) throws IOException {
            checkIfOpen();

            int result = ByteStreams.read(backend, bytes, byteOffset, byteCount);
            if (result > 0) {
                ByteBuffer buffer = get(result);
                buffer.put(bytes, byteOffset, result);
            }

            return result;
        }

        @Override
        public long skip(long byteCount) throws IOException {
            return read(new byte[(int) byteCount]);
        }

        @Override
        public void close() throws IOException {
            freeze();
            backend.close();
            backend = null;
        }
    }
}
