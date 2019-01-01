package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.onErrorResumeEmpty
import kotlinx.coroutines.launch

/**
 */
internal class BookmarkActionItem(private val bookmarkService: BookmarkService, title: String,
                                  private val filter: FeedFilter) : ActionItem(title) {

    override fun enabled(): Boolean {
        return bookmarkService.get()
                .take(1)
                .flatMapIterable { it }
                .onErrorResumeEmpty()
                .exists { it.asFeedFilter() == filter }
                .toBlocking()
                .single()
    }

    override fun activate() {
        AsyncScope.launch {
            bookmarkService.create(filter, title)
        }
    }

    override fun deactivate() {
        bookmarkService.get()
                .take(1)
                .flatMapIterable { it }
                .filter { it.asFeedFilter() == filter }
                .flatMapCompletable { bookmark -> bookmarkService.delete(bookmark).onErrorComplete() }
                .subscribe()
    }
}
