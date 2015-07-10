package com.pr0gramm.app.mpeg;

import android.support.annotation.NonNull;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
        private ByteBuffer current;

        private void freeze() {
            ByteBuffer buffer = current;
            if (buffer != null && buffer.position() > 0) {
                buffer.flip();

                byte[] copy = Arrays.copyOf(buffer.array(), buffer.remaining());
                cache.add(ByteSource.wrap(copy));
            }

            current = null;
        }

        private ByteBuffer get(int space) {
            if (current != null && current.remaining() < space) {
                freeze();
            }

            if (current == null) {
                current = ByteBuffer.allocate(Math.max(space, 64 * 1024));
            }

            return current;
        }

        @Override
        public int read() throws IOException {
            checkIfOpen();

            int result = backend.read();
            if(result >= 0) {
                ByteBuffer buffer = get(1);
                buffer.put((byte) result);
            }

            return result;
        }

        @Override
        public int read(@NonNull byte[] bytes, int byteOffset, int byteCount) throws IOException {
            checkIfOpen();

            int result = ByteStreams.read(backend, bytes, byteOffset, byteCount);
            if(result > 0) {
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
            closed = true;
            backend.close();
        }
    }
}
