package com.pr0gramm.app.cache;

import com.google.common.base.Optional;

import java.io.IOException;

/**
 */
public class CachingUrlInfoResolver implements UrlInfoResolver {
    private final CacheEntryStore entryStore;
    private final UrlInfoResolver fallback;

    public CachingUrlInfoResolver(CacheEntryStore entryStore, UrlInfoResolver fallback) {
        this.entryStore = entryStore;
        this.fallback = fallback;
    }

    @Override
    public UrlInfo resolve(String url) throws IOException {
        Optional<CacheEntry> entry = entryStore.load(url);
        if (entry.isPresent()) {
            int size = entry.get().getSize();
            return new UrlInfo(url, size);
        }

        // do the fallback :/
        return fallback.resolve(url);
    }
}
