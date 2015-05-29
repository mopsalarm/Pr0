package com.pr0gramm.app.api.pr0gramm;

import org.joda.time.Instant;

/**
 */
public class Info {
    private User user;
    private int likeCount;
    private int uploadCount;
    private int commentCount;
    private int tagCount;

    public User getUser() {
        return user;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getUploadCount() {
        return uploadCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getTagCount() {
        return tagCount;
    }

    public static class User {
        private int id;
        private int mark;
        private int score;
        private String name;
        private Instant registered;

        public int getId() {
            return id;
        }

        public int getMark() {
            return mark;
        }

        public int getScore() {
            return score;
        }

        public String getName() {
            return name;
        }

        public Instant getRegistered() {
            return registered;
        }
    }
}
