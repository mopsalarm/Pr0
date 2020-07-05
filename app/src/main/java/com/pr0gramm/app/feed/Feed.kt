package com.pr0gramm.app.feed

import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.listOfSize
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator
import com.pr0gramm.app.parcel.writeValues

/**
 * Represents a feed.
 */
data class Feed(val filter: FeedFilter = FeedFilter(),
                val contentType: Set<ContentType> = setOf(ContentType.SFW),
                val items: List<FeedItem> = listOf(),
                val isAtEnd: Boolean = false,
                val isAtStart: Boolean = false,
                val created: Instant = Instant.now()) : List<FeedItem> by items {


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
        val isAtStart = isAtStart or feed.isAtStart or !feedType.sortable

        val newItems = mergeItems(feed.items.map { FeedItem(it) })
        return copy(items = newItems, isAtStart = isAtStart, isAtEnd = isAtEnd)
    }

    private fun mergeWith(other: Feed): Feed {
        val isAtEnd = isAtEnd or other.isAtEnd
        val isAtStart = isAtStart or other.isAtStart or !feedType.sortable

        val newItems = mergeItems(other.items)
        return copy(items = newItems, isAtStart = isAtStart, isAtEnd = isAtEnd)
    }

    /**
     * Adds the api feed to the items of this feed and returns
     * a list of items.
     */
    private fun mergeItems(newItems: List<FeedItem>): List<FeedItem> {
        // merge them in the correct order.
        val target = ArrayList<FeedItem>(items.size + newItems.size)

        if (feedType.sortable) {
            // get two sorted iterators.
            val old = PeekingIterator(items.sortedWith(itemComparator).iterator())
            val new = PeekingIterator(newItems.sortedWith(itemComparator).iterator())

            while (new.hasNext() && old.hasNext()) {
                val cmp = itemComparator.compare(new.peek(), old.peek())
                target.add(if (cmp > 0) old.next() else new.next())
            }

            // just add the rest
            new.forEach { target.add(it) }
            old.forEach { target.add(it) }
        } else {
            target += this
            target += newItems
        }

        // verify that we did not at an item twice
        return target.distinctBy { it.id }
    }

    fun feedTypeId(item: FeedItem): Long {
        return item.id(feedType)
    }

    fun indexById(itemId: Long): Int? {
        val index = items.indexOfFirst { it.id == itemId }
        return if (index >= 0) index else null
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

    fun parcelAround(pivot: Int): FeedParcel {
        // how many items to save to older and newer than the pivot item.
        val itemCountAround = 64

        // add a subset of the items
        val start = (pivot - itemCountAround).coerceIn(0, items.size)
        val stop = (pivot + itemCountAround).coerceIn(0, items.size)

        return FeedParcel(copy(
                isAtStart = isAtStart && start == 0,
                isAtEnd = false,
                items = this.items.subList(start, stop).toList()))
    }

    /**
     * Merges both feeds
     */
    fun mergeIfPossible(other: Feed): Feed? {
        if (filter != other.filter || contentType != other.contentType)
            return null

        val overlap = other.any { it in this }
        return if (overlap) mergeWith(other) else null
    }

    class FeedParcel(val feed: Feed) : Freezable {
        override fun freeze(sink: Freezable.Sink): Unit = with(sink) {
            sink.write(feed.filter)
            sink.writeInt(ContentType.combine(feed.contentType))
            sink.writeBoolean(feed.isAtStart)
            sink.write(feed.created)
            sink.writeValues(feed.items)
        }

        companion object : Unfreezable<FeedParcel> {
            @JvmField
            val CREATOR = parcelableCreator()

            override fun unfreeze(source: Freezable.Source): FeedParcel = with(source) {
                return FeedParcel(Feed(
                        filter = read(FeedFilter),
                        contentType = ContentType.decompose(readInt()),
                        isAtStart = readBoolean(),
                        created = read(Instant),
                        items = listOfSize(readInt()) { read(FeedItem) }))
            }
        }
    }
}
