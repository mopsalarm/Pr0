package com.pr0gramm.app.services

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.feed.ContentType
import java.util.*
import java.util.concurrent.TimeUnit


/**
 */

class FavedCommentService(private val api: Api, private val userService: UserService) {
    suspend fun list(contentType: EnumSet<ContentType>, olderThan: Instant? = null): List<Api.FavedUserComment> {
        val username = userService.name

        if (!userService.isAuthorized || username == null)
            return listOf()

        val response = api.userCommentsLike(username,
                (olderThan ?: Instant.now().plus(1, TimeUnit.DAYS)).epochSeconds,
                ContentType.combine(contentType))

        return response.comments
    }

    companion object {
        private val regex = "^.*pr0gramm.com/".toRegex()

        fun commentToMessage(comment: Api.FavedUserComment): Message {
            val thumbnail = comment.thumb.replaceFirst(regex, "/")

            return Message(
                    id = comment.id,
                    itemId = comment.itemId,
                    name = comment.name,
                    message = comment.content.trim(),
                    score = comment.up - comment.down,
                    thumbnail = thumbnail,
                    creationTime = comment.created,
                    mark = comment.mark,
                    senderId = 0,
                    type = MessageType.COMMENT,
                    read = true,
                    flags = 0,
                    image = null)
        }
    }
}
