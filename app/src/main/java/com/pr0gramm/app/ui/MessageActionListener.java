package com.pr0gramm.app.ui;

import com.pr0gramm.app.api.pr0gramm.Api;

/**
 */
public interface MessageActionListener {
    /**
     * The user wants to answer to the message with the given id that was
     * written by the given user.
     */
    void onAnswerToPrivateMessage(Api.Message message);

    void onNewPrivateMessage(long userId, String name);

    /**
     * The user clicked on a comment (and probably wants to see that comment now)
     */
    void onCommentClicked(long itemId, long commentId);

    /**
     * The user wants to answer to a comment
     */
    void onAnswerToCommentClicked(Api.Message comment);

    /**
     * A username was clicked.
     */
    void onUserClicked(int userId, String username);
}
