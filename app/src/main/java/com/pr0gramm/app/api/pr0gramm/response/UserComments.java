package com.pr0gramm.app.api.pr0gramm.response;

import org.joda.time.Instant;

import java.util.List;

/**
 */
public class UserComments {
    private User user;
    private List<Comment> comments;

    public User getUser() {
        return user;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public static class Comment {
        private long id;
        private long itemId;
        private Instant created;
        private String thumb;
        private int up;
        private int down;
        private String content;

        public long getId() {
            return id;
        }

        public long getItemId() {
            return itemId;
        }

        public Instant getCreated() {
            return created;
        }

        public String getThumb() {
            return thumb;
        }

        public int getUp() {
            return up;
        }

        public int getDown() {
            return down;
        }

        public String getContent() {
            return content;
        }
    }

    public static class User {
        private int id;
        private int mark;
        private String name;

        public int getId() {
            return id;
        }

        public int getMark() {
            return mark;
        }

        public String getName() {
            return name;
        }
    }
}
