package com.pr0gramm.app.orm

import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.util.tryEnumValueOf
import com.squareup.moshi.JsonClass

/**
 */
@JsonClass(generateAdapter = true)
data class Bookmark(val title: String, val filterTags: String?, val filterUsername: String?, val filterFeedType: String) {
    /**
     * Create a feed filter from this bookmark.
     */
    fun asFeedFilter(): FeedFilter {
        val feedType = tryEnumValueOf<FeedType>(filterFeedType) ?: FeedType.PROMOTED
        var filter = FeedFilter().withFeedType(feedType)

        if (filterTags != null)
            filter = filter.withTags(filterTags)

        if (filterUsername != null)
            filter = filter.withUser(filterUsername)

        return filter
    }

    constructor(title: String, filter: FeedFilter) :
            this(title, filter.tags, filter.username, filter.feedType.name)
}
