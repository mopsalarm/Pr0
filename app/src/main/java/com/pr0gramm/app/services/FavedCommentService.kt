package com.pr0gramm.app.services

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import rx.Observable
import java.util.*
import java.util.concurrent.TimeUnit


/**
 */

class FavedCommentService(private val api: Api, private val userService: UserService) {
    fun list(contentType: EnumSet<ContentType>): Observable<List<Api.FavedUserComment>> {
        val username = userService.name

        if (!userService.isAuthorized || username == null)
            return Observable.just(listOf())

        val comments = api.userCommentsLike(username,
                Instant.now().plus(1, TimeUnit.DAYS).millis,
                ContentType.combine(contentType))

        return comments.map { response -> response.comments }
    }

    companion object {
        private val regex = "^.*pr0gramm.com/".toRegex()

        fun commentToMessage(comment: Api.FavedUserComment): Api.Message {
            val thumbnail = comment.thumb.replaceFirst(regex, "/")
            return Api.Message(
                    id = comment.id,
                    itemId = comment.itemId,
                    name = comment.name,
                    message = comment.content,
                    score = comment.up - comment.down,
                    thumbnail = thumbnail,
                    creationTime = comment.created,
                    mark = comment.mark,
                    senderId = 0
            )
        }
    }
}
