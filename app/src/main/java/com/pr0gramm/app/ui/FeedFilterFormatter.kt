package com.pr0gramm.app.ui

import android.content.Context
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.orm.asFeedFilter
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.util.di.injector

object FeedFilterFormatter {
    /**
     * Simple utility function to format a [com.pr0gramm.app.feed.FeedFilter] to some
     * string. The resulting value can not be parsed back or anything interesting, it is just for
     * display purposes
     */
    fun format(context: Context, filter: FeedFilter): FeedTitle {
        val category = feedTypeToString(context, filter)

        if (!filter.isBasic) {
            // TODO improve formatting logic
            filter.tags?.let { tags ->
                // 'Katze in Top'
                val tagsClean = cleanTags(tags)
                return FeedTitle(category, tagsClean, "$tagsClean in $category")
            }

            filter.collectionTitle?.let { collectionTitle ->
                val titleCollectionOf =
                    context.getString(R.string.filter_format_collection_of, collectionTitle, filter.username)
                return FeedTitle(titleCollectionOf, subtitle = "", singleline = titleCollectionOf)
            }

            filter.username?.let { username ->
                val title = when (filter.feedType) {
                    FeedType.NEW -> context.getString(R.string.uploads)
                    else -> context.getString(R.string.uploads_in, category)
                }

                val subtitle = context.getString(R.string.filter_format_tag_by) + " " + username
                return FeedTitle(title, subtitle, "$subtitle in $category")
            }
        }

        return FeedTitle(feedTypeToString(context, filter), "", "")
    }

    fun feedTypeToString(context: Context, filter: FeedFilter): String {
        if (filter.collectionTitle != null && filter.username != null) {
            return context.getString(R.string.collection_x_of_user, filter.collectionTitle, filter.username)
        }

        if (filter.username != null) {
            return context.getString(R.string.uploads_of, filter.username)
        }

        return when (filter.feedType) {
            FeedType.NEW -> context.getString(R.string.filter_format_new)
            FeedType.BESTOF -> context.getString(R.string.filter_format_bestof)
            FeedType.RANDOM -> context.getString(R.string.filter_format_random)
            FeedType.STALK -> context.getString(R.string.filter_format_premium)
            FeedType.PROMOTED -> context.getString(R.string.filter_format_top)
            FeedType.CONTROVERSIAL -> context.getString(R.string.filter_format_controversial)
            FeedType.JUNK -> context.getString(R.string.action_feed_type_junk)
        }
    }

    fun toTitle(context: Context, filter: FeedFilter): TitleFragment.Title {
        // format the current filter
        val formatted = format(context, filter)

        // get a more specific title from a bookmark if possible
        val bookmarkTitle = bookmarkTitleOf(context, filter)

        return when {
            bookmarkTitle != null ->
                TitleFragment.Title(bookmarkTitle, context.getString(R.string.bookmark))

            else ->
                TitleFragment.Title(formatted.title, formatted.subtitle, useSubtitleInTitle = true)
        }
    }

    private fun bookmarkTitleOf(context: Context, filter: FeedFilter): String? {
        val bookmarkService: BookmarkService = context.injector.instance()

        val directTitle = bookmarkService.byFilter(filter)?.title
        if (directTitle != null) {
            return directTitle
        }

        // check if the filter might match the inverse of any bookmark
        val bookmarkInverse = bookmarkService.all.firstOrNull { bookmark ->
            filter == bookmark.asFeedFilter().invert()
        }

        if (bookmarkInverse != null) {
            return context.getString(R.string.bookmark_inverse_title, bookmarkInverse.title)
        }

        return null
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


    class FeedTitle(val title: String, val subtitle: String, val singleline: String)
}
