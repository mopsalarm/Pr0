package com.pr0gramm.app.feed

/**
 * Type of the feed - like "new" or "top".
 */
enum class FeedType(val searchable: Boolean = true,
                    val preloadable: Boolean = true,
                    val sortable: Boolean = true,
                    val extraCategory: Boolean = false) {
    NEW,
    PROMOTED,
    PREMIUM,
    CONTROVERSIAL(sortable = false, extraCategory = true),
    RANDOM(preloadable = false, sortable = false, extraCategory = true),
    BESTOF(extraCategory = true),
    TEXT(extraCategory = true),
}
