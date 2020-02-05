package com.pr0gramm.app.ui

import com.pr0gramm.app.api.pr0gramm.Message

/**
 */
interface MessageActionListener {
    /**
     * The user wants to answer to the message with the given id that was
     * written by the given user.
     */
    fun onAnswerToPrivateMessage(message: Message)

    fun onNewPrivateMessage(userId: Long, name: String)

    /**
     * The user clicked on a comment (and probably wants to see that comment now)
     */
    fun onCommentClicked(comment: Message)

    /**
     * The user wants to answer to a comment
     */
    fun onAnswerToCommentClicked(comment: Message)

    /**
     * A username was clicked.
     */
    fun onUserClicked(userId: Int, username: String)
}
