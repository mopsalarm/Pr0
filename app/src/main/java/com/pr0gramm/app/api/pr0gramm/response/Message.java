package com.pr0gramm.app.api.pr0gramm.response;

import org.joda.time.Instant;

/**
 * A message received from the pr0gramm api.
 */
public class Message {
    private int id;
    private Instant created;
    private int itemId;
    private int mark;
    private String message;
    private String name;
    private int score;
    private int senderId;
    private String thumb;

    public int getId() {
        return id;
    }

    public Instant getCreated() {
        return created;
    }

    public int getItemId() {
        return itemId;
    }

    public int getMark() {
        return mark;
    }

    public String getMessage() {
        return message;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public int getSenderId() {
        return senderId;
    }

    public String getThumb() {
        return thumb;
    }
}
