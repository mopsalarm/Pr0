package com.pr0gramm.app.feed

import android.os.Bundle
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.api.pr0gramm.Api
import java.util.*

/**
 * Represents a feed.
 */
data class Feed(val filter: FeedFilter = FeedFilter(),
                val contentType: Set<ContentType> = setOf(ContentType.SFW),
                val items: List<FeedItem> = listOf(),
                val isAtEnd: Boolean = false,
                val isAtStart: Boolean = false) : List<FeedItem> by items {


    private val itemComparator = compareByDescending(this::feedTypeId)

    val feedType: FeedType get() = filter.feedType

    val oldest: FeedItem? get() = items.maxWith(itemComparator)
    val newest: FeedItem? get() = items.minWith(itemComparator)

    /**
     * Merges this feed with the provided low level feed representation
     * and returns a new, immutable merged feed.
     */
    fun mergeWith(feed: Api.Feed): Feed {
        val isAtEnd = isAtEnd or feed.isAtEnd
        val isAtStart = isAtStart or feed.isAtStart

        val newItems = mergeItems(feed.items)
        return copy(items = newItems, isAtStart = isAtStart, isAtEnd = isAtEnd)
    }

    /**
     * Adds the api feed to the items of this feed and returns
     * a list of items.
     */
    private fun mergeItems(feedItems: List<Api.Feed.Item>): List<FeedItem> {
        val newItems = feedItems.mapTo(mutableListOf(), ::FeedItem)

        // we can not merge some feeds based on their ids
        if (!feedType.sortable) {
            return newItems
        }

        // get two sorted iterators.
        val old = PeekingIterator(items.sortedWith(itemComparator).iterator())
        val new = PeekingIterator(newItems.sortedWith(itemComparator).iterator())

        // merge them in the correct order.
        val target = ArrayList<FeedItem>(items.size + newItems.size)
        while (new.hasNext() && old.hasNext()) {
            val cmp = itemComparator.compare(new.peek(), old.peek())
            if (cmp > 0) {
                target.add(old.next())
            } else {
                target.add(new.next())
            }
        }

        // just add the rest
        new.forEach { target.add(it) }
        old.forEach { target.add(it) }

        return target
    }

    fun feedTypeId(item: FeedItem): Long {
        return item.id(feedType)
    }

    fun indexById(itemId: Long): Int? {
        val index = items.indexOfFirst { it.id == itemId }
        return if (index >= 0) index else null
    }

    fun persist(pivot: Int): Bundle {
        val bundle = Bundle()
        bundle.putParcelable(FEED_FIELD_FILTER, filter)
        bundle.putInt(FEED_FIELD_CONTENT_TYPE, ContentType.combine(contentType))

        // how many items to save to older and newer than the pivot item.
        val itemCount = if (BuildConfig.DEBUG) 16 else 64

        // add a subset of the items
        val start = (pivot - itemCount).coerceIn(0, items.size)
        val stop = (pivot + itemCount).coerceIn(0, items.size)
        val items = ArrayList(this.items.subList(start, stop))
        bundle.putParcelableArrayList(FEED_FIELD_ITEMS, items)
        bundle.putBoolean(FEED_FIELD_AT_START, isAtStart && start == 0)

        return bundle
    }

    override fun toString(): String {
        return "Feed[newest=${newest?.id}, oldest=${oldest?.id}, size=$size]"
    }

    /**
     * Implementation of PeekingIterator that avoids peeking unless necessary.
     */
    private class PeekingIterator<out E>(private val iterator: Iterator<E>) : Iterator<E> {
        private var hasPeeked: Boolean = false
        private var peekedElement: E? = null

        override fun hasNext(): Boolean {
            return hasPeeked || iterator.hasNext()
        }

        override fun next(): E {
            if (!hasPeeked) {
                return iterator.next()
            }
            val result = peekedElement!!
            hasPeeked = false
            peekedElement = null
            return result
        }

        fun peek(): E? {
            if (!hasPeeked) {
                peekedElement = iterator.next()
                hasPeeked = true
            }

            return peekedElement
        }
    }

    companion object {
        const val FEED_FIELD_FILTER = "filter"
        const val FEED_FIELD_ITEMS = "items"
        const val FEED_FIELD_CONTENT_TYPE = "contentType"
        const val FEED_FIELD_AT_START = "atStart"

        fun restore(bundle: Bundle): Feed {
            val feedFilter = bundle.getParcelable<FeedFilter>(FEED_FIELD_FILTER)
            val items = bundle.getParcelableArrayList<FeedItem>(FEED_FIELD_ITEMS)
            val contentType = ContentType.decompose(bundle.getInt(FEED_FIELD_CONTENT_TYPE))
            val atStart = bundle.getBoolean(FEED_FIELD_AT_START, false)

            return Feed(feedFilter, contentType, items, isAtStart = atStart)
        }
    }
}
