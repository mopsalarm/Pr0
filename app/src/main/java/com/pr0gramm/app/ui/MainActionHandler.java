package com.pr0gramm.app.ui;

import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedProxy;

/**
 */
public interface MainActionHandler {
    void onTagClicked(Tag tag);

    void onPostClicked(FeedProxy feed, int idx);

    void onLogoutClicked();
}
