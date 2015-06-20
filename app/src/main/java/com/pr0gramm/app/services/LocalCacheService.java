package com.pr0gramm.app.services;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.api.pr0gramm.response.Tag;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
@Singleton
public class LocalCacheService {
    private final Cache<Long, ImmutableList<Tag>> tagsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    private final Cache<Long, MetaService.SizeInfo> thumbCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .build();

    /**
     * Caches (or enhanced) a list of tags for the given itemId.
     *
     * @param tags_ The list with the tags you know about
     * @return A list containing all previously seen tags for this item.
     */
    public ImmutableList<Tag> enhanceTags(Long itemId, Iterable<Tag> tags_) {
        ImmutableList<Tag> tags = ImmutableList.copyOf(tags_);

        ImmutableList<Tag> result = tagsCache.getIfPresent(itemId);
        if (result != null) {
            if (tags.size() > 0) {
                // combine only, if we have input tags
                HashSet<Tag> merged = new HashSet<>(result);
                merged.removeAll(tags);
                merged.addAll(tags);
                result = ImmutableList.copyOf(merged);
            }
        } else {
            // no cached, use the newly provided value
            result = ImmutableList.copyOf(tags);
        }

        tagsCache.put(itemId, result);
        return result;
    }

    public void putSizeInfo(MetaService.SizeInfo info) {
        thumbCache.put(info.getId(), info);
    }

    public Optional<MetaService.SizeInfo> getSizeInfo(long itemId) {
        return Optional.fromNullable(thumbCache.getIfPresent(itemId));
    }
}
