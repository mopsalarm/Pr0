package com.pr0gramm.app.vpx;

import android.support.annotation.NonNull;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Uninterruptibles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import okio.Buffer;

/**
 */
public class PreloadingInputStream extends InputStream {
    private static final Logger logger = LoggerFactory.getLogger(PreloadingInputStream.class);
    private static final int CACHE_SIZE = 256 * 1024;

    private final Buffer cache = new Buffer();

    private final InputStream input;
    private volatile boolean inputEof;

    // locking for queue behaviour
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataWritten = lock.newCondition();
    private final Condition dataRead = lock.newCondition();

    // the thread that is doing the reading
    private final Thread thread;
    private volatile IOException exception;

    public PreloadingInputStream(InputStream input) throws IOException {
        this.input = input;
        this.thread = new Thread(this::preload);
        this.thread.start();

        lock.lock();
        try {
            waitReadable(CACHE_SIZE);
        } finally {
            lock.unlock();
        }
    }

    private void preload() {
        byte[] bytes = new byte[1024 * 16];
        try {
            int count;
            do {
                count = ByteStreams.read(input, bytes, 0, bytes.length);
                if (count > 0) {
                    lock.lock();
                    try {
                        while (cache.size() > CACHE_SIZE)
                            dataRead.await();

                        cache.write(bytes, 0, count);

                    } finally {
                        dataWritten.signalAll();
                        lock.unlock();
                    }
                }
            } while (count >= 0);
        } catch (InterruptedException iErr) {
            this.exception = new IOException(iErr);

        } catch (IOException ioError) {
            this.exception = ioError;

        } finally {
            this.inputEof = true;
        }

        lock.lock();
        try {
            dataWritten.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void waitReadable(int amount) throws IOException {
        while (exception == null && !inputEof && cache.size() < amount) {
            try {
                logger.info("cache size at {}, but need {}", cache.size(), amount);
                dataWritten.await();

            } catch (InterruptedException ierr) {
                throw new IOException(ierr);
            }
        }

        if (exception != null)
            throw exception;
    }

    @Override
    public int read() throws IOException {
        lock.lock();
        try {
            waitReadable(1);
            return this.cache.readByte() & 0xff;
        } finally {
            dataRead.signalAll();
            lock.unlock();
        }
    }

    @Override
    public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
        if (byteCount > CACHE_SIZE) {
            int result = 0;
            for (int start = 0; start < byteCount; start += CACHE_SIZE) {
                int read = this.read(buffer, byteOffset + start, CACHE_SIZE);
                if (read < 0)
                    break;

                result += read;
            }

            return result;
        }

        lock.lock();
        try {
            waitReadable(byteCount);
            return this.cache.read(buffer, byteOffset, byteCount);
        } finally {
            dataRead.signalAll();
            lock.unlock();
        }
    }

    @Override
    public void close() {
        thread.interrupt();
        Uninterruptibles.joinUninterruptibly(thread);
    }
}
