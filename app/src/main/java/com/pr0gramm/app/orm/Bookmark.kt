package com.pr0gramm.app.orm

import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.util.tryEnumValueOf

/**
 * Create a feed filter from this bookmark.
 */
fun Bookmark.asFeedFilter(): FeedFilter {
    val feedType = tryEnumValueOf<FeedType>(filterFeedType) ?: FeedType.PROMOTED
    var filter = FeedFilter().withFeedType(feedType)

    filterTags?.let { tags ->
        filter = filter.withTags(tags)
    }

    filterUsername?.let { username ->
        filter = filter.withUser(username)
    }

    return filter
}

fun bookmarkOf(title: String, filter: FeedFilter): Bookmark {
    return Bookmark(title, filter.tags, filter.username, filter.feedType.name)
}