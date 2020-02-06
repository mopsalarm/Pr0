package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.feed.FeedItem

/**
 */
object MessageConverter {
    fun of(item: FeedItem, comment: Api.Comment): Message {
        return Message(
                type = "comment",
                id = comment.id,
                itemId = item.id,
                mark = comment.mark,
                name = comment.name,
                senderId = 0,
                message = comment.content.trim(),
                score = comment.score,
                creationTime = comment.created,
                thumbnail = item.thumbnail,
                read = true,
                flags = 0)
    }

    fun of(sender: Api.UserComments.UserInfo, comment: Api.UserComments.UserComment): Message {
        return of(sender.id, sender.name, sender.mark, comment)
    }

    fun of(sender: Api.Info.User, comment: Api.UserComments.UserComment): Message {
        return of(sender.id, sender.name, sender.mark, comment)
    }

    private fun of(senderId: Int, name: String, mark: Int, comment: Api.UserComments.UserComment): Message {
        return Message(
                type = "comment",
                id = comment.id,
                itemId = comment.itemId,
                mark = mark,
                name = name,
                senderId = senderId,
                message = comment.content.trim(),
                score = comment.score,
                creationTime = comment.created,
                thumbnail = comment.thumb,
                read = true,
                flags = 0)
    }
}

private fun Api.Inbox.Item.toBaseMessage(type: String): Message? {
    if (this.type != type) {
        return null
    }

    return Message(
            type = type,
            id = id,
            read = read,
            creationTime = creationTime,
            thumbnail = thumbnail,
            score = score ?: 0,
            name = name ?: "",
            mark = mark ?: 0,
            senderId = senderId ?: 0,
            itemId = itemId ?: 0,
            message = message?.trim() ?: "",
            flags = flags ?: 0
    )
}

fun Api.Inbox.Item.toPrivateMessage(): Message? {
    return toBaseMessage("message")
}

fun Api.Inbox.Item.toCommentMessage(): Message? {
    return toBaseMessage("comment")
}

fun Api.Inbox.Item.toNotificationMessage(): Message? {
    return toBaseMessage("notification")?.copy(name = "System", mark = 14)
}

fun Api.Inbox.Item.toFollowsMessage(): Message? {
    val msg = toBaseMessage("follows") ?: return null
    return msg.copy(message = "Neuer Hochlad von ${msg.name}")
}

fun Api.Inbox.Item.toMessage(): Message? {
    return toPrivateMessage() ?: toCommentMessage() ?: toNotificationMessage() ?: toFollowsMessage()
}
