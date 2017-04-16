package com.pr0gramm.app.ui.fragments

import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.ui.PreviewInfo

/**
 */
interface PreviewInfoSource {
    /**
     * Returns the preview info for the given feed item.
     */
    fun previewInfoFor(item: FeedItem): PreviewInfo?
}
