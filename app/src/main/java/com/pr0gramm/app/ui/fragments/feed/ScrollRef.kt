package com.pr0gramm.app.ui.fragments.feed

import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.ui.fragments.CommentRef

data class ScrollRef(val ref: CommentRef, val feed: Feed? = null,
                     val autoOpen: Boolean = false,
                     val smoothScroll: Boolean = false,
                     val keepScroll: Boolean = false) {

    val itemId: Long get() = ref.itemId
}