package com.pr0gramm.app.io;

import android.content.Context;
import android.net.Uri;

import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.BuildConfig;

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

import static com.pr0gramm.app.util.AndroidUtility.toFile;

/**
 * A cache we can use for linear caching of http requests.
 */
@Singleton
public class Cache {
    private final long MEGA = 1024 * 1024;

    private final Logger logger = LoggerFactory.getLogger("Cache");

    private final Object lock = new Object();
    private final Map<String, Entry> cache = new HashMap<>();

    private final long maxCacheSize;
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

        // print cache every few seconds for debugging
        if (BuildConfig.DEBUG) {
            Observable
                    .interval(5, TimeUnit.SECONDS, Schedulers.io())
                    .subscribe(v -> printCache());
        }

        this.maxCacheSize = (root.getFreeSpace() > 1024 * MEGA ? (256 * MEGA) : (128 * MEGA));
        logger.debug("Initialized cache with {}mb of space", maxCacheSize / (double) MEGA);
    }

    private void printCache() {
        synchronized (cache) {
            logger.debug("Cache:");
            for (Entry entry : cache.values()) {
                logger.debug("  * {}", entry);
            }
        }
    }

    /**
     * Returns a cached or caching entry. You need to close your reference
     * once you are finish with it.
     */
    public Cache.Entry get(Uri uri) {
        String key = uri.toString();

        synchronized (lock) {
            Entry entry = cache.get(key);
            if (entry == null) {
                entry = createEntry(uri);
                cache.put(key, entry);
            }

            return refCountIfNeeded(entry);
        }
    }

    private Entry refCountIfNeeded(Entry entry) {
        if (entry instanceof CacheEntry) {
            ((CacheEntry) entry).incrementRefCount();
        }

        return entry;
    }

    private Entry createEntry(Uri uri) {
        logger.debug("Creating a new cache entry for uri {}", uri);

        // just open the file directly if it is local.
        if ("file".equals(uri.getScheme())) {
            return new FileEntry(toFile(uri));
        }

        File cacheFile = cacheFileFor(uri);
        return new CacheEntry(httpClient, cacheFile, uri);
    }

    /**
     * Retuns the cache file for the given uri.
     */
    private File cacheFileFor(Uri uri) {
        String filename = uri.toString().replaceFirst("https?://", "").replaceAll("[^a-zA-Z0-9.]+", "_");
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

        logger.debug("Doing cache cleanup, found {} files", files.size());

        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();

            if (totalSize > maxCacheSize) {
                forgetEntryForFile(file);
            }
        }

        logger.debug("Cache took {}mb", totalSize / (1024.f * 1024.f));
    }

    /**
     * Removes the cached entyr with the given filename.
     */
    private void forgetEntryForFile(File file) {
        logger.debug("Remove old cache file {}", file);
        if (!file.delete()) {
            logger.warn("Could not delete cached file {}", file);
        }

        synchronized (lock) {
            for (Entry entry : cache.values()) {
                if (entry instanceof CacheEntry) {
                    if (((CacheEntry) entry).deleteIfClosed()) {
                        // remove the entry from our cache
                        Iterables.removeIf(cache.values(), entry::equals);
                        break;
                    }
                }
            }
        }
    }

    public interface Entry extends Closeable {
        int totalSize() throws IOException;

        InputStream inputStreamAt(int offset) throws IOException;

        /**
         * Returns a value between 0 and 1 that specifies how much of this
         * entry is actually cached. Returns -1 if no estimate is currently available.
         */
        float fractionCached();

        @Override
        void close();
    }
}
