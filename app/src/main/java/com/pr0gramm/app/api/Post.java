package com.pr0gramm.app.api;

import org.joda.time.Instant;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 */
public class Post {
    private List<Tag> tags = emptyList();
    private List<Comment> comments = emptyList();

    public List<Tag> getTags() {
        return tags;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public static class Tag {
        private int id;
        private float confidence;
        private String tag;

        public int getId() {
            return id;
        }

        public float getConfidence() {
            return confidence;
        }

        public String getTag() {
            return tag;
        }
    }

    public static class Comment {
        private int id;
        private float confidence;
        private String name;
        private String content;
        private Instant created;
        private int parent;
        private int up, down;
        private int mark;

        public int getId() {
            return id;
        }

        public float getConfidence() {
            return confidence;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }

        public Instant getCreated() {
            return created;
        }

        public int getParent() {
            return parent;
        }

        public int getUp() {
            return up;
        }

        public int getDown() {
            return down;
        }

        public int getMark() {
            return mark;
        }
    }
}
