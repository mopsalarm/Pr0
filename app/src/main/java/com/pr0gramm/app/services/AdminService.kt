package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem


/**
 */

class AdminService(private val api: Api, private val cacheService: InMemoryCacheService) {
    suspend fun tagsDetails(itemId: Long): Api.TagDetails {
        return api.tagDetails(itemId).await()
    }

    suspend fun deleteItem(item: FeedItem, reason: String, blockDays: Float? = null) {
        val blockUser = if (blockDays != null && blockDays >= 0) "on" else null
        api.deleteItem(null, item.id, "custom", reason, blockUser, blockDays).await()
    }

    suspend fun deleteTags(itemId: Long, tagIds: Collection<Long>, blockDays: Float?) {
        cacheService.invalidate()

        val tags = tagIds.toList()

        val pBlockUser = if (blockDays != null) "on" else null
        api.deleteTag(null, itemId, pBlockUser, blockDays, tags).await()
    }

    suspend fun banUser(name: String, reason: String, blockDays: Float, treeup: Boolean) {
        cacheService.invalidate()

        val mode: Int? = if (treeup) null else 1
        api.userBan(null, name, "custom", reason, blockDays, mode).await()
    }

    suspend fun deleteComment(hard: Boolean, id: Long, reason: String) {
        val func = if (hard) api::hardDeleteComment else api::softDeleteComment
        func(null, id, reason).await()
    }
}