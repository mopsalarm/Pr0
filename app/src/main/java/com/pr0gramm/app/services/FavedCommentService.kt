package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import org.joda.time.Duration
import org.joda.time.Instant
import rx.Observable
import java.util.*


/**
 */

class FavedCommentService(private val api: Api, private val userService: UserService) {
    fun list(contentType: EnumSet<ContentType>): Observable<List<Api.FavedUserComments.FavedUserComment>> {
        if (!userService.isAuthorized)
            return Observable.just(listOf())

        val comments = api.userCommentsLike(userService.name,
                Instant.now().plus(Duration.standardDays(1)).millis,
                ContentType.combine(contentType))

        return comments.map { response -> response.comments }
//
//        return Observable.just(listOf(ImmutableFavedComment.builder()
//                .content("Kommentarfavoriten werden gerade umgebaut. Danke für dein Verständnis.")
//                .created(Instant.now())
//                .up(2)
//                .down(0)
//                .id(1)
//                .itemId(1)
//                .name("Gamb")
//                .mark(3)
//                .thumb("/2018/01/30/6d8fc84f42ab9bca.jpg")
//                .flags(contentType.firstOrNull()?.flag ?: ContentType.SFW.flag)
//                .build()))
    }

    companion object {
        private val regex = "^.*pr0gramm.com/".toRegex()

        fun commentToMessage(comment: Api.FavedUserComments.FavedUserComment): Api.Message {
            val thumbnail = comment.thumb.replaceFirst(regex, "/")
            return com.pr0gramm.app.api.pr0gramm.ImmutableApi.Message.builder()
                    .id(comment.id)
                    .itemId(comment.itemId)
                    .name(comment.name)
                    .message(comment.content)
                    .score(comment.up - comment.down)
                    .thumbnail(thumbnail)
                    .creationTime(comment.created)
                    .mark(comment.mark)

                    /* we dont have the sender :/ */
                    .senderId(0)

                    .build()
        }
    }
}
