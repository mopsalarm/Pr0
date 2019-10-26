package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.feed.FeedItem

/**
 */
object MessageConverter {
    fun of(sender: Api.UserComments.UserInfo, comment: Api.UserComments.UserComment): Api.Message {
        return of(sender.id, sender.name, sender.mark, comment)
    }

    fun of(sender: Api.Info.User, comment: Api.UserComments.UserComment): Api.Message {
        return of(sender.id, sender.name, sender.mark, comment)
    }

    fun of(item: FeedItem, comment: Api.Comment): Api.Message {
        return Api.Message(
                id = comment.id,
                itemId = item.id,
                mark = comment.mark,
                name = comment.name,
                senderId = 0,
                message = comment.content,
                score = comment.score,
                creationTime = comment.created,
                thumbnail = item.thumbnail)
    }

    fun of(item: FeedItem, text: String): Api.Message {
        return Api.Message(
                id = 0,
                itemId = item.id,
                mark = item.mark,
                name = item.user,
                senderId = 0,
                message = text,
                score = item.up - item.down,
                creationTime = item.created,
                thumbnail = item.thumbnail)
    }

    fun of(senderId: Int, name: String, mark: Int, comment: Api.UserComments.UserComment): Api.Message {
        return Api.Message(
                id = comment.id,
                itemId = comment.itemId,
                mark = mark,
                name = name,
                senderId = senderId,
                message = comment.content,
                score = comment.score,
                creationTime = comment.created,
                thumbnail = comment.thumb)
    }
}
