package com.pr0gramm.app.ui

import android.content.Context
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.util.di.injector

object FeedFilterFormatter {
    /**
     * Simple utility function to format a [com.pr0gramm.app.feed.FeedFilter] to some
     * string. The string can not be parsed back or anything interesting.

     * @param context The current context
     * *
     * @param filter  The filter that is to be converted into a string
     */

    fun format(context: Context?, filter: FeedFilter): FeedTitle {
        // prevent null pointer exceptions
        context ?: return FeedTitle("", "", "")

        if (!filter.isBasic) {
            // TODO improve formatting logic
            filter.tags?.let { tags ->
                return FeedTitle(cleanTags(tags), " in ", feedTypeToString(context, filter))
            }

            filter.collectionTitle?.let { collectionTitle ->
                return FeedTitle(
                        context.getString(R.string.filter_format_collection_of, collectionTitle, filter.username),
                        " in ", feedTypeToString(context, filter))
            }

            filter.username?.let {
                return FeedTitle(
                        context.getString(R.string.filter_format_tag_by) + " " + it,
                        " in ", feedTypeToString(context, filter))
            }
        }

        return FeedTitle(feedTypeToString(context, filter), "", "")
    }

    fun feedTypeToString(context: Context, filter: FeedFilter): String {
        if (filter.collection != null) {
            // TODO improve formatting logic
            return context.getString(R.string.favorites_of, filter.username)
        }

        return when (filter.feedType) {
            FeedType.NEW -> context.getString(R.string.filter_format_new)
            FeedType.BESTOF -> context.getString(R.string.filter_format_bestof)
            FeedType.RANDOM -> context.getString(R.string.filter_format_random)
            FeedType.STALK -> context.getString(R.string.filter_format_premium)
            FeedType.PROMOTED -> context.getString(R.string.filter_format_top)
            FeedType.CONTROVERSIAL -> context.getString(R.string.filter_format_controversial)

            else -> throw IllegalArgumentException("Invalid feed type")
        }
    }

    private fun cleanTags(tags: String): String {
        var result = tags.trimStart('?', '!').trim()

        result = trim(result, '(', ')').trim()
        result = trim(result, '"', '"').trim()
        result = trim(result, '\'', '\'').trim()
        result = result.replace(" ([|&-]) ".toRegex(), "$1").trim()

        return result
    }

    private fun trim(text: String, left: Char, right: Char): String {
        if (!text.startsWith(left) || !text.endsWith(right) || text.length <= 2)
            return text

        val mid = text.substring(1, text.lastIndex)
        if (left in mid || right in mid) {
            return text
        }

        return mid
    }

    fun toTitle(context: Context, filter: FeedFilter, titleOverride: String? = null): TitleFragment.Title {
        val bookmarkService: BookmarkService = context.injector.instance()

        // format the current filter
        val formatted = FeedFilterFormatter.format(context, filter)

        // get a more specific title if possible
        val title = titleOverride ?: bookmarkService.byFilter(filter)?.title

        return if (title != null) {
            TitleFragment.Title(title, formatted.singleline, title)
        } else {
            TitleFragment.Title(formatted.title, formatted.subtitle, formatted.singleline)
        }
    }

    class FeedTitle(val title: String, private val separator: String, val subtitle: String) {
        val singleline: String
            get() {
                return title + separator + subtitle
            }
    }
}
