package com.pr0gramm.app.io;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Uninterruptibles;
import com.pr0gramm.app.BuildConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public class GreedyInputStreamCache implements InputStreamCache {
    private static final Logger logger = LoggerFactory.getLogger("GreedyInputStreamCache");

    private final Object lock = new Object();
    private final AtomicBoolean threadStarted = new AtomicBoolean();
    private final Thread thread = new Thread(this::downloadTaskWrapper);
    private final InputStream inputStream;

    private final RandomAccessFile raf;

    private final AtomicInteger openCount = new AtomicInteger();
    private volatile int totalCount = 0;
    private volatile boolean closed = false;
    private volatile boolean endOfStream = false;

    // This will be set if the code set an error.
    private volatile IOException ioError;
    private volatile RuntimeException runtimeError;

    public GreedyInputStreamCache(Context context, InputStream inputStream) throws IOException {
        this.inputStream = inputStream;

        thread.setDaemon(true);
        thread.setName("InputStreamCacher");

        // open a temporary file and delete it from the filesystem.
        File filename = File.createTempFile("video", "tmp", context.getCacheDir());
        raf = new RandomAccessFile(filename, "rw");
        if (!filename.delete())
            logger.warn("Could not delete temporary file");

        // hold at least one reference that we free in close.
        openCount.set(1);

        ensureCachingStarted();
    }

    @Override
    public InputStream get() throws IOException {
        openCount.incrementAndGet();

        return new BufferedInputStream(new InputStream() {
            private int position = 0;

            @Override
            public int read() throws IOException {
                throw new UnsupportedOperationException("Not performant");
            }

            @Override
            public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
                synchronized (lock) {
                    byteCount = waitAndClamp(byteCount);

                    if (byteCount == 0)
                        return -1;

                    if (BuildConfig.DEBUG) {
                        logger.info("Reading {} bytes at pos {}", byteCount, position);
                    }

                    // reading bytes and advancing position.
                    raf.seek(position);
                    raf.readFully(buffer, byteOffset, byteCount);
                    position += byteCount;

                    return byteCount;
                }
            }

            @Override
            public long skip(long n) throws IOException {
                synchronized (lock) {
                    int byteCount = waitAndClamp((int) n);
                    position += byteCount;
                    return byteCount;
                }
            }

            private int waitAndClamp(int byteCount) throws IOException {
                while (position + byteCount > totalCount) {
                    if (endOfStream) {
                        if (position == totalCount) {
                            if (ioError != null) {
                                throw ioError;
                            }

                            if (runtimeError != null) {
                                throw runtimeError;
                            }
                        }

                        byteCount = Math.max(0, totalCount - position);
                        break;
                    }

                    try {
                        lock.wait();
                    } catch (InterruptedException ierr) {
                        throw new IOException("Got interrupted while waiting for data", ierr);
                    }
                }
                return byteCount;
            }

            @Override
            public void close() throws IOException {
                refCountClose();
            }
        }, 1024 * 64);
    }

    @Override
    public void close() throws IOException {
        logger.info("close() called");

        closed = true;
        refCountClose();

        // stop long running operations in the background thread.
        thread.interrupt();
        Uninterruptibles.joinUninterruptibly(thread);
    }

    @Override
    public int cacheSize() {
        return totalCount;
    }

    private void refCountClose() {
        if (openCount.decrementAndGet() == 0) {
            try {
                logger.info("Closing backing file");
                raf.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void ensureCachingStarted() {
        if (threadStarted.compareAndSet(false, true)) {
            this.thread.start();
        }
    }

    private void downloadTaskWrapper() {
        try {
            logger.info("Caching thread started");
            downloadTask();

        } catch (InterruptedException ierr) {
            logger.info("Caching thread got interrupted");

        } catch (IOException error) {
            // we dont really care about io errors here.
            logger.info("Error during caching: " + error);
            this.ioError = error;

        } catch (RuntimeException error) {
            this.runtimeError = error;

        } finally {
            logger.info("Cleaning up in caching thread.");

            // remove caching reference to the stream
            refCountClose();

            this.endOfStream = true;

            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    private void downloadTask() throws IOException, InterruptedException {
        byte[] bytes = new byte[64 * 1024];

        openCount.incrementAndGet();

        int len;
        while (!closed && (len = ByteStreams.read(inputStream, bytes, 0, bytes.length)) > 0) {
            if (Thread.interrupted() || closed)
                throw new InterruptedException();

            synchronized (lock) {
                if (BuildConfig.DEBUG) {
                    logger.info("Writing {} bytes at pos {} to cache", len, totalCount);
                }

                raf.seek(totalCount);
                raf.write(bytes, 0, len);

                totalCount += len;
                lock.notifyAll();
            }
        }
    }
}
