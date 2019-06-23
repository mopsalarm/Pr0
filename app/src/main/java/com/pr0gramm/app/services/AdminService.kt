package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem


/**
 */

class AdminService(private val api: Api, private val cacheService: InMemoryCacheService) {
    private val logger = Logger("AdminService")

    suspend fun tagsDetails(itemId: Long): Api.TagDetails {
        return api.tagDetailsAsync(itemId)
    }

    suspend fun deleteItem(item: FeedItem, reason: String, blockDays: Float? = null) {
        val blockUser = if (blockDays != null && blockDays >= 0) "on" else null
        api.deleteItemAsync(null, item.id, "custom", reason, blockUser, blockDays)
    }

    suspend fun deleteTags(itemId: Long, tagIds: Collection<Long>, blockDays: Float?) {
        cacheService.invalidate()

        val tags = tagIds.toList()

        val pBlockUser = if (blockDays != null) "on" else null
        api.deleteTagAsync(null, itemId, pBlockUser, blockDays, tags)
    }

    suspend fun banUser(name: String, reason: String, days: Float, mode: Api.BanMode) {
        logger.info { "Ban user $name for $days days (mode: $mode): $reason" }
        cacheService.invalidate()

        api.userBanAsync(name, "custom", reason, days, mode)
    }

    suspend fun deleteComment(hard: Boolean, id: Long, reason: String) {
        return if (hard) {
            logger.info { "Doing hard delete of comment $id: $reason" }
            api.hardDeleteCommentAsync(null, id, reason)
        } else {
            logger.info { "Doing soft delete of comment $id: $reason" }
            api.softDeleteCommentAsync(null, id, reason)
        }
    }
}