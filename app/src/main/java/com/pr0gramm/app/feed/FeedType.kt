package com.pr0gramm.app.feed

/**
 * Type of the feed - like "new" or "top".
 */
enum class FeedType(val searchable: Boolean = true,
                    val preloadable: Boolean = true,
                    val sortable: Boolean = true) {
    NEW,
    PROMOTED,
    PREMIUM,
    CONTROVERSIAL(sortable = false),
    RANDOM(preloadable = false, sortable = false),
    BESTOF(),
    TEXT(),
}
