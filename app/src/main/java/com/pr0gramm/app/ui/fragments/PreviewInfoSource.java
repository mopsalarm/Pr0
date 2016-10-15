package com.pr0gramm.app.ui.fragments;

import android.support.annotation.Nullable;

import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.ui.PreviewInfo;

/**
 */
public interface PreviewInfoSource {
    /**
     * Returns the preview info for the given feed item.
     */
    @Nullable
    PreviewInfo previewInfo(FeedItem item);
}
