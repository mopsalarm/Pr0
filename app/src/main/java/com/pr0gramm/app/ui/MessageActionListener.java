package com.pr0gramm.app.ui;

/**
 */
public interface MessageActionListener {
    /**
     * The user wants to answer to the message with the given id that was
     * written by the given user.
     */
    void onAnswerToPrivateMessage(int receiverId, String name);

    /**
     * The user clicked on a comment (and probably wants to see that comment now)
     */
    void onCommentClicked(long itemId, long commentId);

    /**
     * The user wants to answer to a comment
     */
    void onAnswerToCommentClicked(long itemId, long commentId, String name);

    /**
     * A username was clicked.
     */
    void onUserClicked(int userId, String username);
}
