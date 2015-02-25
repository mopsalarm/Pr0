package com.pr0gramm.app.api;

import org.joda.time.Instant;

import java.util.List;

/**
 * Feed class maps the json returned for a call to the
 * api endpoint <code>/api/items/get</code>.
 */
public class Feed {
    boolean atStart;
    boolean atEnd;

    private List<Item> items;

    public boolean isAtStart() {
        return atStart;
    }

    public boolean isAtEnd() {
        return atEnd;
    }

    public List<Item> getItems() {
        return items;
    }

    public static class Item {
        private int id;
        private int mark;
        private String user;
        private int flags;
        private String source;
        private int promoted;
        private int up;
        private int down;
        private Instant created;
        private String image;
        private String thumb;
        private String fullsize;

        public int getId() {
            return id;
        }

        public int getMark() {
            return mark;
        }

        public String getUser() {
            return user;
        }

        public int getFlags() {
            return flags;
        }

        public String getSource() {
            return source;
        }

        public int getPromoted() {
            return promoted;
        }

        public int getUp() {
            return up;
        }

        public int getDown() {
            return down;
        }

        public Instant getCreated() {
            return created;
        }

        public String getImage() {
            return image;
        }

        public String getThumb() {
            return thumb;
        }

        public String getFullsize() {
            return fullsize;
        }
    }
}
