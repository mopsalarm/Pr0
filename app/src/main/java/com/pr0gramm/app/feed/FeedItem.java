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

    /**
     * Gets the id of this feed item depending on the type of the feed..
     *
     * @param type The type of feed.
     */
    public long getId(FeedType type) {
        return type == FeedType.NEW ? item.getId() : item.getPromoted();
    }
}
