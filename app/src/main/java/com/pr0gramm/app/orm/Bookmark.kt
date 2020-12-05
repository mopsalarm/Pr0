package com.pr0gramm.app.orm

import android.net.Uri
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.ui.FilterParser
import com.pr0gramm.app.util.tryEnumValueOf

val Bookmark.isImmutable: Boolean get() = trending

/**
 * Create a feed filter from this bookmark.
 */
fun Bookmark.asFeedFilter(): FeedFilter {
    val link = this._link
    if (link != null) {
        return FilterParser.parse(uri)?.filter ?: FeedFilter()

    } else {
        val feedType = tryEnumValueOf(filterFeedType) ?: FeedType.PROMOTED
        var filter = FeedFilter().withFeedType(feedType)

        filterTags?.let { tags ->
            filter = filter.withTags(tags)
        }

        filterUsername?.let { username ->
            filter = filter.withUser(username)
        }

        return filter
    }
}

fun Bookmark.migrate(): Bookmark {
    // return directly if already migrated
    if (_link != null) {
        return this
    }

    val filter = asFeedFilter()

    val uri = Uri.parse("https://pr0gramm.com/").buildUpon()

    when {
        filter.username != null ->
            uri.appendPath("user").appendPath(filter.username).appendPath("uploads")

        filter.feedType == FeedType.PROMOTED ->
            uri.appendPath("top")

        else ->
            uri.appendPath("new")
    }

    if (filter.tags != null) {
        uri.appendPath(filter.tags)
    }

    return Bookmark(title, _link = uri.build().encodedPath, trending = trending)
}

val Bookmark.link: String
    get() = migrate()._link!!

val Bookmark.uri: Uri
    get() = Uri.parse("https://pr0gramm.com").buildUpon().encodedPath(link).build()

fun bookmarkOf(title: String, filter: FeedFilter): Bookmark {
    return Bookmark(title, filter.tags, filter.username, filter.feedType.name)
}
