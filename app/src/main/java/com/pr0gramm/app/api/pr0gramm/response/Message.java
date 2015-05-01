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

    @SuppressWarnings("unused")
    public Message() {
        // for reflection
    }

    private Message(Builder builder) {
        id = builder.id;
        created = builder.created;
        itemId = builder.itemId;
        mark = builder.mark;
        message = builder.message;
        name = builder.name;
        score = builder.score;
        senderId = builder.senderId;
        thumb = builder.thumb;
    }

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


    public static final class Builder {
        private int id;
        private Instant created;
        private int itemId;
        private int mark;
        private String message;
        private String name;
        private int score;
        private int senderId;
        private String thumb;

        public Builder() {
        }

        public Builder(Message copy) {
            this.id = copy.id;
            this.created = copy.created;
            this.itemId = copy.itemId;
            this.mark = copy.mark;
            this.message = copy.message;
            this.name = copy.name;
            this.score = copy.score;
            this.senderId = copy.senderId;
            this.thumb = copy.thumb;
        }

        public Builder withId(int id) {
            this.id = id;
            return this;
        }

        public Builder withCreated(Instant created) {
            this.created = created;
            return this;
        }

        public Builder withItemId(int itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder withMark(int mark) {
            this.mark = mark;
            return this;
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withScore(int score) {
            this.score = score;
            return this;
        }

        public Builder withSenderId(int senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder withThumb(String thumb) {
            this.thumb = thumb;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}
