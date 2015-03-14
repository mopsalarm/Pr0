package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.google.inject.Singleton;
import com.pr0gramm.app.feed.FeedItem;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import javax.inject.Inject;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

/**
 * Very simple service to check if an item was already visited or not.
 */
@Singleton
public class SeenService {
    private final Object lock = new Object();
    private final Observable<ByteBuffer> buffer;

    @Inject
    public SeenService(Context context) {
        File file = new File(context.getCacheDir(), "seen-posts.bits");
        buffer = Async.fromCallable(() -> mapByteBuffer(file), Schedulers.io()).cache();

        // subscribe once so that the value is cached for the next time.
        buffer.subscribe();
    }

    public boolean isSeen(FeedItem item) {
        int idx = (int) item.getId() / 8;

        ByteBuffer buffer = this.buffer.toBlocking().first();
        if (idx < 0 || idx >= buffer.limit()) {
            Log.w("SeenService", "Id is too large");
            return false;
        }

        int mask = 1 << (7 - item.getId() % 8);
        return (buffer.get(idx) & mask) != 0;
    }

    public void markAsSeen(FeedItem item) {
        int idx = (int) item.getId() / 8;

        ByteBuffer buffer = this.buffer.toBlocking().first();
        if (idx < 0 || idx >= buffer.limit())
            Log.w("SeenService", "Id is too large");

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
        synchronized (lock) {
            ByteBuffer buffer = this.buffer.toBlocking().first();
            for (int idx = 0; idx < buffer.limit(); idx++) {
                buffer.put(idx, (byte) 0);
            }
        }
    }


    @SuppressLint("NewApi")
    private static ByteBuffer mapByteBuffer(File file) throws IOException {
        // space for up to two million posts
        final long size = 2_000_000 / 8;

        Log.i("SeenService", "Mapping cache: " + file);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size);
            return raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
        }
    }
}
