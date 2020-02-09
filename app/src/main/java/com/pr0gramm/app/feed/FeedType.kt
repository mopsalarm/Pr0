package com.pr0gramm.app.feed

import androidx.annotation.Keep

/**
 * Type of the feed - like "new" or "top".
 */
@Keep
enum class FeedType(val searchable: Boolean = true,
                    val preloadable: Boolean = true,
                    val sortable: Boolean = true) {
    NEW,
    PROMOTED,
    STALK,
    CONTROVERSIAL(sortable = false),
    RANDOM(preloadable = false, sortable = false),
    BESTOF,
}
