package com.pr0gramm.app.services

import com.google.common.primitives.Longs
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import gnu.trove.set.TLongSet
import rx.Completable
import rx.Observable


/**
 */

class AdminService(private val api: Api, private val cacheService: InMemoryCacheService) {
    fun tagsDetails(itemId: Long): Observable<Api.TagDetails> {
        return api.tagDetails(itemId)
    }

    fun deleteItem(item: FeedItem, reason: String, blockDays: Float? = null): Completable {
        val blockUser = if (blockDays != null && blockDays >= 0) "on" else null

        return api
                .deleteItem(null, item.id, "custom", reason, blockUser, blockDays)
                .toCompletable()
    }

    fun deleteTags(itemId: Long, tagIds: TLongSet, blockDays: Float?): Completable {
        cacheService.invalidate()

        val pBlockUser = if (blockDays != null) "on" else null
        return api
                .deleteTag(null, itemId, pBlockUser, blockDays, Longs.asList(*tagIds.toArray()))
                .toCompletable()
    }

    fun banUser(name: String, reason: String, blockDays: Float, treeup: Boolean): Completable {
        cacheService.invalidate()

        val mode: Int? = if (treeup) null else 1
        return api
                .userBan(null, name, "custom", reason, blockDays, mode)
                .toCompletable()
    }

    fun deleteComment(hard: Boolean, id: Long, reason: String): Completable {
        val func = if (hard) api::hardDeleteComment else api::softDeleteComment
        return func(null, id, reason).toCompletable()
    }
}