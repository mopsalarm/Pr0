package com.pr0gramm.app.cache;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.pr0gramm.app.cache.access.DataAccess;
import com.pr0gramm.app.cache.access.ReportingDataAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 */
public class CacheManager implements OnWriteListener {
    private final Object lock = new Object();
    private final Map<String, CacheEntryHolder> entries = new HashMap<>();
    private final Set<CacheEntry> dirty = Sets.newIdentityHashSet();

    private final LoggingAdapter logger;
    private final CacheEntryStore cacheEntryStore;
    private final DataAccess.DataAccessFactory dataAccessFactory;
    private final UrlInfoResolver urlInfoResolver;
    private final TimeSource timeSource;

    public CacheManager(LoggingAdapter logger, CacheEntryStore cacheEntryStore,
                        DataAccess.DataAccessFactory dataAccessFactory,
                        UrlInfoResolver urlInfoResolver, TimeSource timeSource) {

        this.logger = logger;
        this.cacheEntryStore = cacheEntryStore;
        this.dataAccessFactory = dataAccessFactory;
        this.timeSource = timeSource;

        // add caching to the resolver
        this.urlInfoResolver = new CachingUrlInfoResolver(cacheEntryStore, urlInfoResolver);
    }

    public InputStream open(String url) throws IOException {
        UrlInfoResolver.UrlInfo urlInfo = urlInfoResolver.resolve(url);

        synchronized (lock) {
            CacheEntryHolder holder = getCacheEntry(urlInfo.getUrl(), urlInfo.getSize());

            // if the cached file is fully available, just return it
            if (holder.entry.isFullyAvailable()) {
                return new DataAccessInputStreamAdapter(holder.data, holder.entry.getBlockCount());
            }
        }

        throw new UnsupportedOperationException("not yet implemented");
    }

    private CacheEntryHolder getCacheEntry(String url, int size) {
        synchronized (lock) {
            CacheEntryHolder holder = entries.get(url);
            if (holder == null) {
                // get the cache and data-handle for this url
                CacheEntry entry = cacheEntryStore.acquire(url, size);
                DataAccess data = openDataAccess(entry);

                // subscribe to updates
                entry.subscribe(this);

                // and remember it for later.
                holder = new CacheEntryHolder(size, entry, data);
                entries.put(url, holder);
            }
            holder.touch();
            return holder;
        }
    }

    private DataAccess openDataAccess(CacheEntry cacheEntry) {
        File file = cacheEntryStore.dataFile(cacheEntry);
        int blockSize = cacheEntry.getBlockSize();
        int blockCount = cacheEntry.getBlockCount();

        DataAccess data;
        try {
            data = dataAccessFactory.newDataAccess(file, blockSize, blockCount);
        } catch (IOException io) {
            throw Throwables.propagate(io);
        }

        // now wrap to report writings to the cache entry.
        return new ReportingDataAccess(cacheEntry, data);
    }

    /**
     * Removes the entry from this manager, if it has no
     * further listeners except the {@link com.pr0gramm.app.cache.CacheManager}
     * itself.
     */
    public void tryRemoveCacheEntry(CacheEntry entry) {
        synchronized (lock) {
            if (entry.getSubscriberCount() != 1)
                return;

            entry.unsubscribe(this);

            CacheEntryHolder holder = entries.remove(entry.getUrl());
            checkNotNull(holder, "Entry was not in the cache");
            holder.data.close();
        }
    }

    @Override
    public void onWrite(CacheEntry cacheEntry, int block) {
        synchronized (dirty) {
            // mark entry dirty after writing to it
            dirty.add(cacheEntry);
        }
    }

    /**
     * Flushes dirty cache entries back to disk.
     */
    private void flushDirtyCacheEntries() {
        synchronized (dirty) {
            for (CacheEntry cacheEntry : dirty) {
                flushCacheEntry(cacheEntry);
            }

            dirty.clear();
        }
    }

    /**
     * Flushes the given cache item. Will not throw an error,
     * if the item could not be flushed.
     *
     * @param cacheEntry The item to flush.
     */
    private void flushCacheEntry(CacheEntry cacheEntry) {
        try {
            cacheEntryStore.store(cacheEntry);

        } catch (RuntimeException error) {
            String msg = String.format("Could not flush cache item for %s",
                    cacheEntry.getUrl());

            logger.error(msg, error);
        }
    }

    private class CacheEntryHolder {
        final int size;
        final CacheEntry entry;
        final DataAccess data;
        long lastAccess;

        public CacheEntryHolder(int size, CacheEntry entry, DataAccess data) {
            this.size = size;
            this.entry = entry;
            this.data = data;
            this.lastAccess = timeSource.now();
        }

        /**
         * Marks this entry as modified right now.
         */
        public void touch() {
            lastAccess = timeSource.now();
        }
    }

    public interface LoggingAdapter {
        void error(String format, Throwable error);
    }
}
