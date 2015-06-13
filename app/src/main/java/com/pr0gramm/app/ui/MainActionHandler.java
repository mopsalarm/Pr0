package com.pr0gramm.app.ui;

import com.pr0gramm.app.feed.Feed;
import com.pr0gramm.app.feed.FeedFilter;

/**
 */
public interface MainActionHandler {
    void onPostClicked(Feed feed, int idx);

    void onLogoutClicked();

    void onFeedFilterSelected(FeedFilter filter);

    void pinFeedFilter(FeedFilter filter, String title);
}
