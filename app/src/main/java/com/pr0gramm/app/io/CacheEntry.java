package com.pr0gramm.app.io;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Completable;
import rx.functions.Action0;
import rx.functions.Actions;
import rx.schedulers.Schedulers;

/**
 * A entry that is hold by the {@link Cache}.
 */
final class CacheEntry implements Cache.Entry {
    private static final int PAYLOAD_OFFSET = 16;

    private final Logger logger = LoggerFactory.getLogger("CacheEntry");

    private final Object lock = new Object();
    private final AtomicInteger refCount = new AtomicInteger();
    private final OkHttpClient httpClient;
    private final Uri uri;
    private final File file;

    private volatile RandomAccessFile fp;
    private volatile int totalSize;
    private volatile int written;
    private volatile boolean caching;

    CacheEntry(OkHttpClient httpClient, File file, Uri uri) {
        this.httpClient = httpClient;
        this.file = file;
        this.uri = uri;
    }

    int read(int pos, byte[] data, int offset, int amount) throws IOException {
        // Always succeed when reading 0 bytes.
        if (amount == 0) {
            return 0;
        }

        ensureInitialized();

        // if we are at the end of the file, we need to signal that
        if (pos >= totalSize) {
            return -1;
        }

        // check how much we can actually read at most!
        amount = Math.min(pos + amount, totalSize) - pos;

        synchronized (lock) {
            expectWritten(pos + amount);

            // okay, we should be able to get the data now.
            seek(pos);

            // now try to read the bytes we requested
            int byteCount = read(fp, data, offset, amount);

            // check if we got as much bytes as we wanted to.
            if (byteCount != amount) {
                AndroidUtility.logToCrashlytics(
                        new EOFException(String.format("Expected to read %d bytes at %d, but got only %d. Cache entry: %s", amount, pos, byteCount, this)));
            }

            return byteCount;
        }
    }

    /**
     * Reads the given number of bytes from the current position of the stream
     * if possible. The method returns the numbers of bytes actually read.
     */
    private static int read(RandomAccessFile fp, byte[] data, int offset, int amount) throws IOException {
        int totalCount = 0;

        do {
            int count = fp.read(data, offset + totalCount, amount - totalCount);
            if (count < 0) {
                break;
            }

            totalCount += count;
        } while (totalCount < amount);

        return totalCount;
    }


    @Override
    public int totalSize() throws IOException {
        ensureInitialized();
        return totalSize;
    }

    private void write(byte[] data, int offset, int amount) throws IOException {
        ensureInitialized();

        synchronized (lock) {
            // only really write if we have a positive amount here.
            if (amount > 0) {
                seek(written);
                fp.write(data, offset, amount);
                written += amount;
            }

            // tell the readers about the new data.
            lock.notifyAll();
        }
    }

    /**
     * Waits until at least the given amount of data is written.
     */
    private void expectWritten(int requiredCount) throws IOException {
        try {
            while (written < requiredCount) {
                ensureCaching();
                lock.wait(2500);
            }
        } catch (InterruptedException err) {
            throw new InterruptedIOException("Waiting for bytes was interrupted.");
        }
    }

    private void seek(int pos) throws IOException {
        fp.seek(PAYLOAD_OFFSET + pos);
    }

    private void ensureInitialized() throws IOException {
        synchronized (lock) {
            // we are initialized if we already have a opened file.
            if (fp != null) {
                return;
            }

            logger.debug("Entry needs to be initialized: {}", this);
            initialize();
        }
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet and currently not caching.
     */
    private void ensureCaching() throws IOException {
        if (!caching && !fullyCached()) {
            logger.debug("Caching will start on entry {}", this);
            resumeCaching(written);
        }
    }

    private void cachingStarted() {
        logger.debug("Caching starts now.");

        synchronized (lock) {
            incrementRefCount();
            caching = true;
        }
    }

    /**
     * This method is called from the caching thread once caching stops.
     */
    private void cachingStopped() {
        logger.debug("Caching stopped on entry {}", this);
        synchronized (lock) {
            if (caching) {
                caching = false;
                close();
            }

            // If there are any readers, we need to notify them, so caching will be
            // re-started if needed
            lock.notifyAll();
        }
    }

    /**
     * Returns true, if the entry is fully cached.
     * You need to hold the lock to call this method.
     */
    private boolean fullyCached() {
        return written >= totalSize;
    }


    /**
     * Will be called if we need to initialize the file. If this is called, we can expect
     * the entry to hold its own lock.
     */
    private void initialize() throws IOException {
        // ensure that the parent directory exists.
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                logger.warn("Could not create parent directory.");
            }
        }

        // we expect the hash of the filename as the checksum.
        int expectedChecksum = file.getName().hashCode();

        // open the file in read/write mode, creating it if it did not exist.
        fp = new RandomAccessFile(file, "rw");
        try {
            // get the length of the file and the checksum to test if we just created it,
            // or if it already contains data.
            int length = (int) fp.length();
            boolean fileIsValid = length >= 8 && fp.readInt() == expectedChecksum;

            if (fileIsValid) {
                logger.debug("Found already cached file, loading metadata.");

                // read the total size from the file now.
                // We've previously read the first four bytes (checksum).
                totalSize = fp.readInt();
                written = Math.max(0, length - PAYLOAD_OFFSET);
            } else {
                logger.debug("Entry is new, no data is previously cached.");
                // we can not have written anything yet.
                written = 0;

                // start caching now.
                totalSize = resumeCaching(0);

                // write header at the beginning of the file
                fp.getChannel().truncate(0);
                fp.writeInt(expectedChecksum);
                fp.writeInt(totalSize);
            }

            logger.debug("Initialized entry {}", this);

        } catch (IOException err) {
            // resetting fp on error.
            Closeables.close(fp, true);
            fp = null;

            throw err;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void writeResponseToEntry(Response response) {
        byte[] buffer = new byte[1024 * 64];
        try (InputStream stream = response.body().byteStream()) {
            while (true) {
                // read a chunk of data
                int byteCount = ByteStreams.read(stream, buffer, 0, buffer.length);
                if (byteCount <= 0)
                    break;

                // and put it into the entry.
                write(buffer, 0, byteCount);
            }

            // sync file to disk
            fp.getFD().sync();

        } catch (IOException error) {
            logger.error("Could not buffer the complete response.", error);

        } finally {
            cachingStopped();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private int resumeCaching(int offset) throws IOException {

        cachingStarted();
        try {
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header("Range", String.format("bytes=%d-", offset))
                    .build();

            logger.debug("Resume caching for {}", this);
            Response response = httpClient.newCall(request).execute();
            try {
                if (response.code() == 403)
                    throw new IOException("Not allowed to read file, are you on a public wifi?");

                if (response.code() == 404)
                    throw new FileNotFoundException("File not found at " + response.request().url());

                if (response.code() != 206)
                    throw new IOException("Expected status code 206, got " + response.code());

            } catch (IOException | RuntimeException err) {
                response.close();
                throw err;
            }

            // read the response in some other thread.
            doAsync(() -> writeResponseToEntry(response));

            return (int) response.body().contentLength();

        } catch (IOException | RuntimeException err) {
            cachingStopped();
            throw err;
        }
    }

    private void doAsync(Action0 runnable) {
        Completable.fromAction(runnable)
                .subscribeOn(Schedulers.io())
                .subscribe(Actions.empty(), err -> logger.error("Error in background thread", err));
    }

    /**
     * Increment the refCount
     */
    CacheEntry incrementRefCount() {
        refCount.incrementAndGet();
        return this;
    }

    /**
     * Deletes the file if it is currently closed.
     */
    boolean deleteIfClosed() {
        synchronized (lock) {
            if (fp != null) {
                return false;
            }

            if (!file.delete()) {
                logger.warn("Could not delete file {}", file);
                return false;
            }

            // deletion went good!
            return true;
        }
    }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    @Override
    public void close() {
        this.refCount.decrementAndGet();
        synchronized (lock) {

            // try to correct value on error :/
            int refCount;
            while ((refCount = this.refCount.get()) < 0) {
                logger.warn("ref-Count is less than zero. This shouldn't happen.");
                this.refCount.compareAndSet(refCount, 0);
            }

            // close if ref count is zero.
            if (this.refCount.get() <= 0 && this.fp != null) {
                logger.debug("Closing cache file for entry {} now.", this);

                try {
                    this.fp.close();
                } catch (IOException ignored) {
                }

                this.fp = null;
            }
        }
    }

    @Override
    public InputStream inputStreamAt(int position) {
        // update the time stamp if the cache file already exists.
        if (file.exists() && !file.setLastModified(System.currentTimeMillis())) {
            logger.warn("Could not update timestamp on {}", file);
        }

        return new EntryInputStream(incrementRefCount(), position);
    }

    @Override
    public float fractionCached() {
        if (totalSize > 0) {
            return written / (float) totalSize;
        } else {
            return -1;
        }
    }

    /**
     * Returns the number of bytes that are available too read without caching
     * from the given position.
     */
    private int availableStartingAt(int position) {
        synchronized (lock) {
            return Math.max(0, written - position);
        }
    }


    @Override
    public String toString() {
        synchronized (lock) {
            return MoreObjects.toStringHelper(this)
                    .add("written", written)
                    .add("totalSize", totalSize)
                    .add("caching", caching)
                    .add("refCount", refCount.get())
                    .add("fullyCached", fullyCached())
                    .add("uri", uri)
                    .toString();
        }
    }

    private static class EntryInputStream extends InputStream {
        private final CacheEntry entry;
        private int position;
        private int mark;

        EntryInputStream(CacheEntry entry, int position) {
            this.entry = entry;
            this.position = position;
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("read() not implemented.");
        }

        @Override
        public int read(@NonNull byte[] bytes, int off, int len) throws IOException {
            int byteCount = entry.read(position, bytes, off, len);
            if (byteCount > 0) {
                position += byteCount;
            }

            return byteCount;
        }

        @Override
        public long skip(long amount) throws IOException {
            if (amount < 0) {
                return 0;
            }

            amount = Math.min(entry.totalSize(), position + amount) - position;
            position += amount;
            return amount;
        }

        @Override
        public void close() throws IOException {
            entry.close();
        }

        @Override
        public int available() throws IOException {
            return entry.availableStartingAt(position);
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readlimit) {
            mark = position;
        }

        @Override
        public synchronized void reset() throws IOException {
            position = mark;
        }
    }
}
