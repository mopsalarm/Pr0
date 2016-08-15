package com.pr0gramm.app.feed;

/**
 * Type of the feed - like "new" or "top".
 */
public enum FeedType {
    NEW {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return true;
        }

        @Override
        public boolean sortable() {
            return true;
        }
    },

    PROMOTED {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return true;
        }

        @Override
        public boolean sortable() {
            return true;
        }
    },

    PREMIUM {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return true;
        }

        @Override
        public boolean sortable() {
            return true;
        }
    },

    CONTROVERSIAL {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return true;
        }

        @Override
        public boolean sortable() {
            return false;
        }
    },

    RANDOM {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return false;
        }

        @Override
        public boolean sortable() {
            return false;
        }
    },

    BESTOF {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return true;
        }

        public boolean sortable() {
            return true;
        }
    },

    TEXT {
        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean preloadable() {
            return true;
        }

        public boolean sortable() {
            return true;
        }
    };

    public abstract boolean searchable();

    public abstract boolean preloadable();

    public abstract boolean sortable();
}
