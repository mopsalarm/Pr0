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

interface TitleFragment {
    /**
     * Optional title directly provided by the fragment.
     */
    val title: Title?

    data class Title(val title: String? = null, val subtitle: String? = null)
}
