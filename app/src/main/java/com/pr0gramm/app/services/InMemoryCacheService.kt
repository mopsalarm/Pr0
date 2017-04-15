package com.pr0gramm.app.services

import com.google.common.base.Optional
import com.google.common.cache.CacheBuilder
import com.google.common.collect.ImmutableList
import com.google.common.primitives.Longs
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedItem
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
@Singleton
class InMemoryCacheService @Inject
constructor() {
    private val tagsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build<Long, ImmutableList<Api.Tag>>()

    private val userInfoCache = CacheBuilder.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build<String, EnhancedUserInfo>()

    private val repostCache = AtomicReference(LongArray(0))

    /**
     * Caches (or enhanced) a list of tags for the given itemId.

     * @param tags_ The list with the tags you know about
     * *
     * @return A list containing all previously seen tags for this item.
     */
    fun enhanceTags(itemId: Long, tags: List<Api.Tag>): ImmutableList<Api.Tag> {
        val result = tagsCache.getIfPresent(itemId)?.let { cached ->
            if (tags.isNotEmpty()) {
                val merged = HashSet(cached)
                merged.addAll(tags)
                ImmutableList.copyOf(merged)
            } else {
                cached
            }
        } ?: ImmutableList.copyOf(tags)

        tagsCache.put(itemId, result)
        return result
    }

    /**
     * Caches the given items as reposts.
     */
    @Synchronized
    fun cacheReposts(newRepostIds: List<Long>) {
        val reposts = TreeSet<Long>()
        reposts.addAll(repostCache.get().asList())
        reposts.addAll(newRepostIds)

        repostCache.set(Longs.toArray(reposts))
    }

    /**
     * Checks if the given item is a repost or not.

     * @param itemId The item to check
     */
    fun isRepost(itemId: Long): Boolean {
        return Arrays.binarySearch(repostCache.get(), itemId) >= 0
    }

    fun isRepost(item: FeedItem): Boolean {
        return isRepost(item.id())
    }

    /**
     * Stores the given entry for a few minutes in the cache
     */
    fun cacheUserInfo(contentTypes: Set<ContentType>, info: EnhancedUserInfo) {
        val name = info.info.user.name.trim().toLowerCase()
        val key = name + ContentType.combine(contentTypes)
        userInfoCache.put(key, info)
    }

    /**
     * Gets a cached instance, if there is one.
     */
    fun getUserInfo(contentTypes: Set<ContentType>, name: String): Optional<EnhancedUserInfo> {
        val key = name.trim().toLowerCase() + ContentType.combine(contentTypes)
        return Optional.fromNullable(userInfoCache.getIfPresent(key))
    }

    /**
     * Caches the given tags. They will be added with an id of 0 and a confidence value of 0.5f.
     */
    fun cacheTags(itemId: Long, tags: List<String>) {
        enhanceTags(itemId, tags.map { tag ->
            com.pr0gramm.app.api.pr0gramm.ImmutableApi.Tag.builder()
                    .tag(tag).id(0).confidence(0.5f)
                    .build()
        })
    }
}
