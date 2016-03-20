package com.pr0gramm.app.mpeg;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static com.google.common.io.ByteStreams.copy;
import static com.google.common.io.Closeables.closeQuietly;

/**
 */
public class InputStreamCacheImpl implements InputStreamCache {
    private final File directory;
    private InputStream backend;
    private RandomAccessFile cache;
    private CachingInputStream cachingStream;

    public InputStreamCacheImpl(Context context, InputStream backend) {
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

    private void closeBackend() {
        closeQuietly(backend);
        backend = null;
    }

    private boolean backendIsClosed() {
        return backend == null;
    }

    private void validateBackendOpen() {
        if (backendIsClosed()) {
            throw new IllegalStateException("input stream is closed.");
        }
    }

    @Override
    public synchronized InputStream get() throws IOException {
        if (backendIsClosed()) {
            // reset the cache and return a new input stream.
            cache.seek(0);

            ParcelFileDescriptor fd = ParcelFileDescriptor.dup(cache.getFD());
            return new FilterInputStream(new FileInputStream(cache.getFD())) {
                @Override
                public void close() throws IOException {
                    try {
                        fd.close();
                    } catch (IOException ignored) {
                    }

                    super.close();
                }
            };
        } else if (cachingStream != null) {
            copy(cachingStream, ByteStreams.nullOutputStream());
            closeQuietly(cachingStream);
            cachingStream = null;
            return get();

        } else {
            cache = newCacheFile();
            cachingStream = new CachingInputStream();
            return cachingStream;
        }
    }

    @Override
    public void close() throws IOException {
        closeQuietly(backend);

        if (cache != null) {
            cache.close();
        }
    }

    private class CachingInputStream extends InputStream {
        private ByteBuffer current;

        private void dumpToCache() throws IOException {
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
                dumpToCache();
            }

            if (current == null || current.remaining() < space) {
                current = ByteBuffer.allocate(Math.max(space, 64 * 1024));
            }

            return current;
        }

        @Override
        public int read() throws IOException {
            validateBackendOpen();

            int result = backend.read();
            if (result >= 0) {
                ByteBuffer buffer = get(1);
                buffer.put((byte) result);
            }

            return result;
        }

        @Override
        public int read(@NonNull byte[] bytes, int byteOffset, int byteCount) throws IOException {
            validateBackendOpen();

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
            dumpToCache();
            closeBackend();
        }
    }
}
