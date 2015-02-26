package com.pr0gramm.app.api;

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
        private long id;
        private long promoted;
        private String image;
        private String thumb;
        private String fullsize;
        private String user;
        private int up;

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
    }
}
