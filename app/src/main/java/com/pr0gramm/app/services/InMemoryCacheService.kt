package com.pr0gramm.app.services

import androidx.collection.LruCache
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.catchAll
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


/**
 * This service helps to locally cache deltas to the pr0gramm. Those
 * deltas might arise because of cha0s own caching.
 */
class InMemoryCacheService {
    private val tagsCache = LruCache<Long, ExpiringValue<List<Api.Tag>>>(256)
    private val userInfoCache = LruCache<String, ExpiringValue<UserInfo>>(24)
    private val repostCache = AtomicReference(LongSparseArray<Unit>())

    /**
     * Invalidates all caches
     */
    @Synchronized
    fun invalidate() {
        tagsCache.evictAll()
        userInfoCache.evictAll()
    }

    /**
     * Caches (or enhanced) a list of tags for the given itemId.

     * @param tags The list with the tags you know about
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
    private fun cacheReposts(newRepostIds: List<Long>) {
        if (newRepostIds.isEmpty())
            return

        synchronized(repostCache) {
            val copy = repostCache.get().clone()
            newRepostIds.forEach { copy.put(it, Unit) }
            repostCache.set(copy)
        }
    }

    /**
     * Checks if the given item is a repost or not.

     * @param itemId The item to check
     */
    fun isRepost(itemId: Long): Boolean {
        return repostCache.get().contains(itemId)
    }

    fun isRepost(item: FeedItem): Boolean {
        return isRepost(item.id)
    }

    /**
     * Stores the given entry for a few minutes in the cache
     */
    fun cacheUserInfo(contentTypes: Set<ContentType>, info: UserInfo) {
        val name = info.info.user.name.trim().lowercase(Locale.getDefault())
        val key = name + ContentType.combine(contentTypes)
        userInfoCache.put(key, ExpiringValue(info, 1, TimeUnit.MINUTES))
    }

    /**
     * Gets a cached instance, if there is one.
     */
    fun getUserInfo(contentTypes: Set<ContentType>, name: String): UserInfo? {
        val key = name.trim().lowercase(Locale.getDefault()) + ContentType.combine(contentTypes)
        return userInfoCache[key]?.value
    }

    /**
     * Caches the given tags. They will be added with an id of 0 and a confidence value of 0.5f.
     */
    fun cacheTags(itemId: Long, tags: List<String>) {
        enhanceTags(itemId, tags.map { tag -> Api.Tag(0L, 0.5f, tag) })
    }

    suspend fun refreshRepostsCache(feedService: FeedService, query: FeedService.FeedQuery): Boolean {
        catchAll {
            return withContext(NonCancellable) {
                val feed = feedService.load(query)
                cacheReposts(feed.items.map { it.id })
                true
            }
        }

        return false
    }

    private class ExpiringValue<out T : Any>(value: T, expireTime: Long, timeUnit: TimeUnit) {
        private val deadline: Long = System.currentTimeMillis() + timeUnit.toMillis(expireTime)

        val expired: Boolean get() = System.currentTimeMillis() > deadline

        @Suppress("RedundantNullableReturnType")
        val value: T? = value
            get() = field.takeIf { !expired }
    }
}
