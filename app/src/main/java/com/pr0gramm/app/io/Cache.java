package com.pr0gramm.app.io;

import android.content.Context;
import android.net.Uri;

import com.google.common.collect.Ordering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import rx.Observable;
import rx.schedulers.Schedulers;

/**
 * A cache we can use for linear caching of http requests.
 */
@Singleton
public class Cache {
    private static final int MAX_CACHE_SIZE = 256 * 1024 * 1024;

    private final Logger logger = LoggerFactory.getLogger("Cache");

    private final Object lock = new Object();
    private final Map<String, CacheEntry> cache = new HashMap<>();

    private final File root;
    private final OkHttpClient httpClient;

    @Inject
    public Cache(Context context, OkHttpClient httpClient) {
        this.root = new File(context.getCacheDir(), "mediacache");
        this.httpClient = httpClient;

        // schedule periodic cache clean up.
        Observable
                .interval(10, 60, TimeUnit.SECONDS, Schedulers.io())
                .doOnNext(v -> cleanupCache())
                .onErrorResumeNext(Observable.empty())
                .subscribe();

        Observable
                .interval(5, TimeUnit.SECONDS, Schedulers.io())
                .subscribe(v -> printCache());
    }

    private void printCache() {
        synchronized (cache) {
            logger.info("Cache:");
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                logger.info("  {}: {}", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Returns a cached or caching entry. You need to close your reference
     * once you are finish with it.
     */
    public Cache.Entry entryOf(Uri uri) {
        String key = uri.toString();

        synchronized (lock) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                entry = createEntry(uri);
                cache.put(key, entry);
            }

            return entry.incrementRefCount();
        }
    }

    private CacheEntry createEntry(Uri uri) {
        logger.info("Creating a new cache entry for uri {}", uri);
        File cacheFile = cacheFileFor(uri);
        return new CacheEntry(httpClient, cacheFile, uri);
    }

    /**
     * Retuns the cache file for the given uri.
     */
    private File cacheFileFor(Uri uri) {
        String filename = uri.toString().replaceFirst("https?://", "").replaceAll("[^a-z0-9.]+", "_");
        return new File(root, filename);
    }

    /**
     * Checks for old files in the cache directory and removes the oldest files
     * if they exceed the maximum cache size.
     */
    private void cleanupCache() {
        if (!root.exists()) {
            return;
        }

        List<File> files = Ordering.natural()
                .onResultOf(File::lastModified)
                .reverse()
                .sortedCopy(Arrays.asList(root.listFiles()));

        logger.info("Doing cache cleanup, found {} files", files.size());

        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();

            if (totalSize > MAX_CACHE_SIZE) {
                forgetEntryForFile(file);
            }
        }
    }

    /**
     * Removes the cached entyr with the given filename.
     */
    private void forgetEntryForFile(File file) {
        logger.info("Remove old cache file {}", file);
        if (!file.delete()) {
            logger.warn("Could not delete cached file {}", file);
        }

        synchronized (lock) {
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                if (entry.getValue().file.getName().equals(file.getName())) {
                    // remove the entry from our cache
                    cache.remove(entry.getKey());

                    // close our reference to the entry
                    entry.getValue().close();

                    break;
                }
            }
        }
    }

    public interface Entry extends Closeable {
        int read(int pos, byte[] bytes, int offset, int length) throws IOException;

        int totalSize() throws IOException;

        InputStream inputStreamAt(int offset);

        /**
         * Returns a value between 0 and 1 that specifies how much of this
         * entry is actually cached. Returns -1 if no estimate is currently available.
         */
        float fractionCached();

        @Override
        void close();
    }
}
