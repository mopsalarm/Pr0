package com.pr0gramm.app.services

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.db.FavedCommentsQueries
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.toStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.util.EnumSet
import java.util.concurrent.TimeUnit


/**
 */

class FavedCommentService(private val api: Api, private val db: FavedCommentsQueries) {

    // observe all entries in the database and push updates to the state flow.
    private val latest: StateFlow<Set<Long>> = db.all().asFlow().mapToList(Dispatchers.IO)
        .map { HashSet(it) }
        .toStateFlow(AsyncScope, initialValue = setOf())


    suspend fun list(contentType: EnumSet<ContentType>, username: String, olderThan: Instant? = null): List<Api.FavedUserComment> {
        val response = api.userCommentsLike(username,
                (olderThan ?: Instant.now().plus(1, TimeUnit.DAYS)).epochSeconds,
                ContentType.combine(contentType))

        return response.comments
    }

    suspend fun markAsFaved(commentId: Long) {
        api.commentsFav(null, commentId)
        db.insert(commentId)
    }

    suspend fun markAsNotFaved(commentId: Long) {
        api.commentsUnfav(null, commentId)
        db.remove(commentId)
    }

    fun isFaved(commentId: Long): Boolean {
        return commentId in latest.value
    }

    fun observeCommentIsFaved(commentId: Long): Flow<Boolean> {
        return latest.map { ids -> commentId in ids }
    }

    fun clear() {
        db.removeAll()
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
