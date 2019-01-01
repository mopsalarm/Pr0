package com.pr0gramm.app.orm

import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType

/**
 */
data class Bookmark(val title: String, val filterTags: String?, val filterUsername: String?, val filterFeedType: String) {

    fun asFeedFilter(): FeedFilter {
        var filter = FeedFilter().withFeedType(FeedType.valueOf(filterFeedType))
        if (filterTags != null)
            filter = filter.withTags(filterTags)

        if (filterUsername != null)
            filter = filter.withUser(filterUsername)

        return filter
    }
}
