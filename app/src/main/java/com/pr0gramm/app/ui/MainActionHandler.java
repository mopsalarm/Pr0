package com.pr0gramm.app.ui;

import com.pr0gramm.app.feed.FeedFilter;

/**
 */
public interface MainActionHandler {
    void onLogoutClicked();

    void onFeedFilterSelected(FeedFilter filter);

    void pinFeedFilter(FeedFilter filter, String title);

    void showUploadBottomSheet();
}
