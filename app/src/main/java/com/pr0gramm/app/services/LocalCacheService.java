package com.pr0gramm.app.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.api.pr0gramm.response.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import static com.google.common.collect.Lists.newArrayList;

/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
@Singleton
public class LocalCacheService {
    private final Cache<Long, List<Tag>> tagsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    /**
     * Caches (or enhanced) a list of tags for the given itemId.
     *
     * @param tags The list with the tags you know about
     * @return A list containing all previously seen tags for this item.
     */
    public List<Tag> enhanceTags(Long itemId, Iterable<Tag> tags) {
        ArrayList<Tag> tagList = newArrayList(tags);

        List<Tag> cached = tagsCache.getIfPresent(itemId);
        if (cached != null) {
            for (Tag tag : cached) {
                if (!tagList.contains(tag)) {
                    tagList.add(0, tag);
                }
            }
        }

        tagsCache.put(itemId, tagList);
        return ImmutableList.copyOf(tagList);
    }
}
