package com.pr0gramm.app.api.pr0gramm.response;

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

    @SuppressWarnings("unused")
    public static class Item {
        private long id;
        private long promoted;
        private String image;
        private String thumb;
        private String fullsize;
        private String user;
        private int up, down;
        private int mark;
        private int flags;
        private Instant created;

        public long getId() {
            return id;
        }

        public long getPromoted() {
            return promoted;
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

        public String getUser() {
            return user;
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

        public int getFlags() {
            return flags;
        }

        public Instant getCreated() {
            return created;
        }
    }
}
