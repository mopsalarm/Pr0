package com.pr0gramm.app.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.pr0gramm.app.api.pr0gramm.response.Tag;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
@Singleton
public class LocalCacheService {
    private final Cache<Long, List<Tag>> tagsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    /**
     * Caches (or enhanced) a list of tags for the given itemId.
     *
     * @param tags The list with the tags you know about
     * @return A list containing all previously seen tags for this item.
     */
    public ImmutableList<Tag> enhanceTags(Long itemId, Iterable<Tag> tags) {
        ImmutableList<Tag> result;

        List<Tag> cached = tagsCache.getIfPresent(itemId);
        if (cached != null) {
            HashSet<Tag> combined = new HashSet<>(cached);
            Iterables.addAll(combined, tags);
            result = ImmutableList.copyOf(combined);
        } else {
            result = ImmutableList.copyOf(tags);
        }

        tagsCache.put(itemId, result);
        return result;
    }
}
