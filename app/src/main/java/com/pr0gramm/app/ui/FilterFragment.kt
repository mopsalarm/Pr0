package com.pr0gramm.app.ui

import com.pr0gramm.app.feed.FeedFilter

/**
 */
interface FilterFragment {
    /**
     * Returns the current filter of this instance.
     */
    val currentFilter: FeedFilter
}
