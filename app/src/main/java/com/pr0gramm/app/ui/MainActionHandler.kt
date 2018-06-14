package com.pr0gramm.app.ui

import android.os.Bundle
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.ui.fragments.CommentRef

/**
 */
interface MainActionHandler {
    fun onLogoutClicked()

    fun onFeedFilterSelected(filter: FeedFilter)

    fun onFeedFilterSelected(filter: FeedFilter, searchQueryState: Bundle?)

    fun onFeedFilterSelected(filter: FeedFilter, queryState: Bundle?,
                             startAt: CommentRef?, popBackstack: Boolean = false)

    fun pinFeedFilter(filter: FeedFilter, title: String)

    fun showUploadBottomSheet()

}
