package com.pr0gramm.app.ui;

/**
*/
public interface MessageActionListener {
    /**
     * The user wants to answer to the message with the given id that was
     * written by the given user.
     */
    void onAnswerToPrivateMessage(int receiverId, String name);
}
