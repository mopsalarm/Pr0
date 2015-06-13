package com.pr0gramm.app.ui;

import com.google.common.base.Optional;
import com.pr0gramm.app.feed.Feed;
import com.pr0gramm.app.feed.FeedFilter;

/**
 */
public interface MainActionHandler {
    void onPostClicked(Feed feed, int idx, Optional<Long> commentId);

    void onLogoutClicked();

    void onFeedFilterSelected(FeedFilter filter);

    void pinFeedFilter(FeedFilter filter, String title);
}
