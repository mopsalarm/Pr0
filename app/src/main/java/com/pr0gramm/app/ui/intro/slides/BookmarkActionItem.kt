package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.bookmarkOf
import com.pr0gramm.app.services.BookmarkService

/**
 */
internal class BookmarkActionItem(private val bookmarkService: BookmarkService, title: String,
                                  private val filter: FeedFilter) : ActionItem(title) {

    override fun enabled(): Boolean {
        return bookmarkService.byTitle(title) != null || bookmarkService.byFilter(filter) != null
    }

    override fun activate() {
        deactivate()
        bookmarkService.save(bookmarkOf(title, filter))
    }

    override fun deactivate() {
        // delete any existing bookmarks
        bookmarkService.byTitle(title)?.let { bookmarkService.delete(it) }
        bookmarkService.byFilter(filter)?.let { bookmarkService.delete(it) }
    }
}
