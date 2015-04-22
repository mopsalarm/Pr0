package com.pr0gramm.app.api.pr0gramm.response;

import org.joda.time.Instant;

/**
 */
public class PrivateMessage {
    private int id;
    private Instant created;
    private int recipientId;
    private int recipientMark;
    private String recipientName;
    private int senderId;
    private int senderMark;
    private String senderName;
    private boolean sent;

    public int getId() {
        return id;
    }

    public Instant getCreated() {
        return created;
    }

    public int getRecipientId() {
        return recipientId;
    }

    public int getRecipientMark() {
        return recipientMark;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getSenderMark() {
        return senderMark;
    }

    public String getSenderName() {
        return senderName;
    }

    public boolean isSent() {
        return sent;
    }
}
