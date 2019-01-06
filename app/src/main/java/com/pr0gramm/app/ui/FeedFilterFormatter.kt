package com.pr0gramm.app.ui

import android.content.Context
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType

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
        if (context == null)
            return FeedTitle("", "", "")

        if (!filter.isBasic) {
            filter.tags?.let {
                return FeedTitle(it, " in ", feedTypeToString(context, filter))
            }

            filter.username?.let {
                return FeedTitle(
                        context.getString(R.string.filter_format_tag_by) + " " + it,
                        " in ", feedTypeToString(context, filter))
            }

            filter.likes?.let {
                return FeedTitle(
                        context.getString(R.string.filter_format_fav_of) + " " + it,
                        " in ", feedTypeToString(context, filter))
            }
        }

        return FeedTitle(feedTypeToString(context, filter), "", "")
    }


    fun feedTypeToString(context: Context, filter: FeedFilter): String {
        if (filter.likes != null) {
            return context.getString(R.string.favorites_of, filter.likes)
        }

        when (filter.feedType) {
            FeedType.NEW -> return context.getString(R.string.filter_format_new)
            FeedType.TEXT -> return context.getString(R.string.filter_format_text)
            FeedType.BESTOF -> return context.getString(R.string.filter_format_bestof)
            FeedType.RANDOM -> return context.getString(R.string.filter_format_random)
            FeedType.PREMIUM -> return context.getString(R.string.filter_format_premium)
            FeedType.PROMOTED -> return context.getString(R.string.filter_format_top)
            FeedType.CONTROVERSIAL -> return context.getString(R.string.filter_format_controversial)

            else -> throw IllegalArgumentException("Invalid feed type")
        }
    }

    class FeedTitle(val title: String, val separator: String, val subtitle: String) {
        val singleline: String get() {
            return title + separator + subtitle
        }
    }
}
