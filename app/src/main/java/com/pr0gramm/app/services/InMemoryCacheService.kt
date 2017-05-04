package com.pr0gramm.app.services

import android.support.v4.util.LruCache
import com.google.common.primitives.Longs
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedItem
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
class InMemoryCacheService {
    private val tagsCache = LruCache<Long, ExpiringValue<List<Api.Tag>>>(256)
    private val userInfoCache = LruCache<String, ExpiringValue<EnhancedUserInfo>>(24)

    private val repostCache = AtomicReference(LongArray(0))

    /**
     * Caches (or enhanced) a list of tags for the given itemId.

     * @param tags_ The list with the tags you know about
     * *
     * @return A list containing all previously seen tags for this item.
     */
    fun enhanceTags(itemId: Long, tags: List<Api.Tag>): List<Api.Tag> {
        val result = tagsCache.get(itemId)?.value?.let { cached ->
            if (tags.isNotEmpty()) {
                (HashSet(cached) + tags).toList()
            } else {
                cached
            }
        } ?: tags.toList()

        tagsCache.put(itemId, ExpiringValue(result, 5, TimeUnit.MINUTES))
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
        userInfoCache.put(key, ExpiringValue(info, 1, TimeUnit.MINUTES))
    }

    /**
     * Gets a cached instance, if there is one.
     */
    fun getUserInfo(contentTypes: Set<ContentType>, name: String): EnhancedUserInfo? {
        val key = name.trim().toLowerCase() + ContentType.combine(contentTypes)
        return userInfoCache[key]?.value
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

    private class ExpiringValue<out T : Any>(value: T, expireTime: Long, timeUnit: TimeUnit) {
        private val deadline: Long = System.currentTimeMillis() + timeUnit.toMillis(expireTime)

        val expired: Boolean get() = System.currentTimeMillis() > deadline

        val value: T? = value
            get() = field.takeIf { !expired }
    }
}
