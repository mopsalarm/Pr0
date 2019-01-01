package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.ui.base.AsyncScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 */
internal class BookmarkActionItem(private val bookmarkService: BookmarkService, title: String,
                                  private val filter: FeedFilter) : ActionItem(title) {

    override fun enabled(): Boolean {
        return runBlocking {
            bookmarkService.exists(filter)
        }
    }

    override fun activate() {
        AsyncScope.launch {
            bookmarkService.create(filter, title)
        }
    }

    override fun deactivate() {
        AsyncScope.launch {
            bookmarkService.delete(filter)
        }
    }
}
