package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.feed.FeedItem

/**
 */
object MessageConverter {
    fun of(item: FeedItem, comment: Api.Comment): Message {
        return Message(
                type = MessageType.COMMENT,
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
                image = null,
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
                type = MessageType.COMMENT,
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
                image = null,
                flags = 0)
    }
}

private fun Api.Inbox.Item.toBaseMessage(type: MessageType): Message? {
    if (this.type != type.apiValue) {
        return null
    }

    return Message(
            type = type,
            id = id,
            read = read,
            creationTime = creationTime,
            thumbnail = thumbnail,
            image = image,
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
    return toBaseMessage(MessageType.MESSAGE)
}

fun Api.Inbox.Item.toCommentMessage(): Message? {
    return toBaseMessage(MessageType.COMMENT)
}

fun Api.Inbox.Item.toNotificationMessage(): Message? {
    return toBaseMessage(MessageType.NOTIFICATION)?.copy(name = "", mark = 0)
}

fun Api.Inbox.Item.toStalkMessage(): Message? {
    val msg = toBaseMessage(MessageType.STALK) ?: return null
    return msg.copy(message = "Neuer Hochlad von ${msg.name}")
}

fun Api.Inbox.Item.toMessage(): Message? {
    return toPrivateMessage() ?: toCommentMessage() ?: toNotificationMessage() ?: toStalkMessage()
}
