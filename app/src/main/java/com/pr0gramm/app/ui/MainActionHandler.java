package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.ui.fragments.ItemWithComment;

/**
 */
public interface MainActionHandler {
    void onLogoutClicked();

    void onFeedFilterSelected(FeedFilter filter);

    void onFeedFilterSelected(FeedFilter filter, @Nullable Bundle searchQueryState);

    void onFeedFilterSelected(FeedFilter filter, @Nullable Bundle searchQueryState,
                              @Nullable ItemWithComment startAt);

    void pinFeedFilter(FeedFilter filter, String title);

    void showUploadBottomSheet();

}
