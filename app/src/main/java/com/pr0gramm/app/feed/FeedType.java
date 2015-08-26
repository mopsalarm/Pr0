package com.pr0gramm.app.feed;

/**
 * Type of the feed - like "new" or "top".
 */
public enum FeedType {
    NEW, PROMOTED, PREMIUM,

    CONTROVERSIAL {
        @Override
        public boolean searchable() {
            return false;
        }

        @Override
        public boolean preloadable() {
            return true;
        }
    },

    RANDOM {
        @Override
        public boolean searchable() {
            return false;
        }

        @Override
        public boolean preloadable() {
            return false;
        }
    };

    public boolean searchable() {
        return true;
    }

    public boolean preloadable() {
        return true;
    }

}
