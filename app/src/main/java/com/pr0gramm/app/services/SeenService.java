package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Singleton;
import com.pr0gramm.app.feed.FeedItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import javax.inject.Inject;

/**
 * Very simple service to check if an item was already visited or not.
 */
@Singleton
public class SeenService {
    private static final Logger logger = LoggerFactory.getLogger(SeenService.class);

    private final Object lock = new Object();
    private final SettableFuture<ByteBuffer> buffer = SettableFuture.create();

    @Inject
    public SeenService(Context context) {
        File file = new File(context.getCacheDir(), "seen-posts.bits");

        AsyncTask.execute(() -> {
            try {
                buffer.set(mapByteBuffer(file));
            } catch (IOException error) {
                logger.warn("Could not load the seen-Cache");
            }
        });
    }

    public boolean isSeen(FeedItem item) {
        return isSeen(item.getId());
    }

    public boolean isSeen(long id) {
        if (!this.buffer.isDone())
            return false;

        int idx = (int) id / 8;

        ByteBuffer buffer = Futures.getUnchecked(this.buffer);
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn("Id is too large");
            return false;
        }

        int mask = 1 << (7 - id % 8);
        return (buffer.get(idx) & mask) != 0;
    }

    public void markAsSeen(FeedItem item) {
        if (!this.buffer.isDone())
            return;

        int idx = (int) item.getId() / 8;

        ByteBuffer buffer = Futures.getUnchecked(this.buffer);
        if (idx < 0 || idx >= buffer.limit()) {
            logger.warn("Id is too large");
            return;
        }

        // only one thread can write the buffer at a time.
        synchronized (lock) {
            byte value = buffer.get(idx);
            value |= 1 << (7 - item.getId() % 8);
            buffer.put(idx, value);
        }
    }

    /**
     * Removes the "marked as seen" status from all items.
     */
    public void clear() {
        if (!this.buffer.isDone())
            return;

        ByteBuffer buffer = Futures.getUnchecked(this.buffer);

        synchronized (lock) {
            logger.info("Removing all the items");
            for (int idx = 0; idx < buffer.limit(); idx++) {
                buffer.put(idx, (byte) 0);
            }
        }
    }

    /**
     * Maps the cache into a byte buffer. The buffer is backed by the file, so
     * all changes to the buffer are written back to the file.
     *
     * @param file The file to map into memory
     */
    @SuppressLint("NewApi")
    private static ByteBuffer mapByteBuffer(File file) throws IOException {
        // space for up to two million posts
        final long size = 2_000_000 / 8;

        logger.info("Mapping cache: " + file);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
            return raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
        }
    }
}
