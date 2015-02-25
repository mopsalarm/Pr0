package com.pr0gramm.app.feed;

import com.pr0gramm.app.api.Feed;

/**
 * This is an item in pr0gramm feed item to be displayed. It is backed
 * by a {@link com.pr0gramm.app.api.Feed.Item} and enhanced with meta data,
 * like "already seen".
 */
public class FeedItem {
    private final Feed.Item item;
    private final boolean seen;

    public FeedItem(Feed.Item item, boolean seen) {
        this.item = item;
        this.seen = seen;
    }

    public Feed.Item getItem() {
        return item;
    }

    public boolean isSeen() {
        return seen;
    }

    public long getId() {
        return item.getId();
    }
}
