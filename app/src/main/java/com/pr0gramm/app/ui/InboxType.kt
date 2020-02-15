package com.pr0gramm.app.ui

import com.pr0gramm.app.api.pr0gramm.MessageType

/**
 * Type of inbox
 */
enum class InboxType {
    PRIVATE,
    ALL,
    COMMENTS_IN,
    COMMENTS_OUT,
    STALK,
    NOTIFICATIONS;

    companion object {
        fun forMessageType(messageType: MessageType): InboxType {
            return when (messageType) {
                MessageType.STALK -> STALK
                MessageType.MESSAGE -> PRIVATE
                MessageType.COMMENT -> COMMENTS_IN
                MessageType.NOTIFICATION -> NOTIFICATIONS
            }
        }
    }
}
