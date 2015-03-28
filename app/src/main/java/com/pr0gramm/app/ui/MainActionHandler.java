package com.pr0gramm.app.ui;

import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedProxy;

/**
 */
public interface MainActionHandler {
    void onPostClicked(FeedProxy feed, int idx);

    void onLogoutClicked();

    void onFeedFilterSelected(FeedFilter filter);

    void pinFeedFilter(FeedFilter filter, String title);
}
