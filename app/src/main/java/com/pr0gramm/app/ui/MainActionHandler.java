package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.pr0gramm.app.feed.FeedFilter;

/**
 */
public interface MainActionHandler {
    void onLogoutClicked();

    void onFeedFilterSelected(FeedFilter filter);

    void onFeedFilterSelected(FeedFilter filter, @Nullable Bundle searchQueryState);

    void pinFeedFilter(FeedFilter filter, String title);

    void showUploadBottomSheet();

}
