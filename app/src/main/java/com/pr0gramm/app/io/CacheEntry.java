package com.pr0gramm.app.io;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class CacheEntry implements Cache.Entry {
    private static final int PAYLOAD_OFFSET = 16;

    private final Logger logger = LoggerFactory.getLogger("CacheEntry");

    private final Object lock = new Object();
    private final AtomicInteger refCount = new AtomicInteger();
    private final OkHttpClient httpClient;
    private final Uri uri;

    final File file;

    private RandomAccessFile fp;
    private int totalSize;
    private int written;

    private boolean caching;

    CacheEntry(OkHttpClient httpClient, File file, Uri uri) {
        this.httpClient = httpClient;
        this.file = file;
        this.uri = uri;
    }

    @Override
    public int read(int pos, byte[] data, int offset, int amount) throws IOException {
        ensureInitialized();

        // check how much we can actually read at most!
        amount = Math.min(pos + amount, totalSize) - pos;

        // we are at end of file
        if (amount <= 0) {
            return 0;
        }

        synchronized (lock) {
            expectWritten(pos + amount);

            // okay, we should be able to get the data now.
            seek(pos);
            fp.readFully(data, offset, amount);

            return amount;
        }
    }


    @Override
    public int totalSize() throws IOException {
        ensureInitialized();
        return totalSize;
    }

    private void write(byte[] data, int offset, int amount) throws IOException {
        ensureInitialized();

        synchronized (lock) {
            seek(written);
            fp.write(data, offset, amount);
            written += amount;

            // tell the readers about the new data.
            lock.notifyAll();
        }
    }

    /**
     * Waits until at least the given amount of data is written.
     */
    private void expectWritten(int byteCount) throws IOException {
        try {
            while (written < byteCount) {
                ensureCachingIfNeeded();
                lock.wait();
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

            logger.info("Entry needs to be initialized: {}", this);
            initialize();
        }
    }

    /**
     * Ensure that the entry is caching data. Caching is needed, if it is not fully
     * cached yet, and currently not caching
     */
    private void ensureCachingIfNeeded() throws IOException {
        if (!caching && !fullyCached()) {
            logger.info("Caching will start on entry {}", this);
            resumeCaching(written);
        }
    }

    private void cachingStarted() {
        logger.info("Caching starts now.");

        synchronized (this) {
            incrementRefCount();
            caching = true;
        }
    }

    /**
     * This method is called from the caching thread once caching stops.
     */
    private void cachingStopped() {
        logger.info("Caching stopped on entry {}", this);
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

        // open the file in read/write mode, creating it if it did not exist.
        fp = new RandomAccessFile(file, "rw");
        try {
            // get the length of the file to check if we just created it, or if it already
            // contains data.
            int length = (int) fp.length();
            boolean newlyCreated = length < 4;

            if (newlyCreated) {
                logger.info("Entry is new, no data is previously cached.");

                // start caching now.
                totalSize = resumeCaching(0);
                fp.writeInt(totalSize);

            } else {
                logger.info("Found already cached file, loading metadata.");

                totalSize = fp.readInt();
                written = Math.max(0, length - PAYLOAD_OFFSET);
            }

            logger.info("Initialized entry {}", this);

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

        } catch (IOException error) {
            logger.error("Could not buffer the complete response.", error);

        } finally {
            cachingStopped();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private int resumeCaching(int offset) throws IOException {
        Response response = null;

        cachingStarted();

        try {
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header("Range", String.format("bytes=%d-", offset))
                    .build();

            logger.info("Resume caching for {}", this);
            response = httpClient.newCall(request).execute();
            if (response.code() == 404)
                throw new FileNotFoundException("File not found at " + response.request().url());

            if (response.code() != 206)
                throw new IOException("Expected status code 206, got " + response.code());

            // now that we have the response, we'll write it to the file in
            // another thread
            int contentLength = (int) response.body().contentLength();

            Response r = response;
            doAsync(() -> writeResponseToEntry(r));

            return contentLength;

        } catch (IOException err) {
            if (response != null) {
                response.close();
            }

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
    Cache.Entry incrementRefCount() {
        refCount.incrementAndGet();
        return this;
    }

    /**
     * Mark this entry as "closed" - as far as the caller is concerned. The entry
     * itself does not need to close immediately if it is used somewhere else.
     */
    @Override
    public void close() {
        synchronized (lock) {
            this.refCount.decrementAndGet();

            // try to correct value on error :/
            int refCount;
            while ((refCount = this.refCount.get()) < 0) {
                logger.warn("ref-Count is less than zero. This shouldn't happen.");
                this.refCount.compareAndSet(refCount, 0);
            }

            // close if ref count is zero.
            if (this.refCount.get() <= 0 && this.fp != null) {
                logger.info("Closing cache file for entry {} now.", this);

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
        if (!file.setLastModified(System.currentTimeMillis())) {
            logger.warn("Could not update timestamp on {}", file);
        }

        incrementRefCount();
        return new EntryInputStream(this, position);
    }

    @Override
    public float fractionCached() {
        if (totalSize > 0) {
            return written / (float) totalSize;
        } else {
            return -1;
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
            position += byteCount;

            return byteCount;
        }

        @Override
        public void close() throws IOException {
            entry.close();
        }
    }

}
