package com.pr0gramm.app.feed

import android.os.Parcel
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.*

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

    val oldestItem: FeedItem? get() = items.maxWithOrNull(itemComparator)
    val newestItem: FeedItem? get() = items.minWithOrNull(itemComparator)

    /**
     * Merges this feed with the provided low level feed representation
     * and returns a new, immutable merged feed.
     */
    fun mergeWith(update: Api.Feed): Feed {
        val isAtEnd = isAtEnd or update.isAtEnd
        val isAtStart = isAtStart or update.isAtStart or !feedType.sortable

        val newItems = mergeItems(update.items.map { FeedItem(it) })
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
        val target = ArrayList<FeedItem>(items.size + newItems.size)

        // add them to the target
        target += this
        target += newItems

        if (feedType.sortable) {
            // sort them correctly
            target.sortWith(itemComparator)
        }

        // and remove any duplicates in the end.
        return target.distinctBy { it.id }
    }

    fun feedTypeId(item: FeedItem): Long {
        return item.id(feedType)
    }

    operator fun contains(id: Long): Boolean {
        return any { item -> item.id == id }
    }

    fun indexById(itemId: Long): Int? {
        val index = items.indexOfFirst { it.id == itemId }
        return if (index >= 0) index else null
    }

    override fun toString(): String {
        return "Feed(newest=${newestItem?.id}, oldest=${oldestItem?.id}, size=$size, filter=$filter)"
    }

    fun parcelAround(pivot: Int): FeedParcel {
        // how many items to save to older and newer than the pivot item.
        val itemCountAround = 16

        // add a subset of the items
        val start = (pivot - itemCountAround).coerceIn(0, items.size)
        val stop = (pivot + itemCountAround).coerceIn(0, items.size)

        return FeedParcel(this, copy(
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

    class FeedParcel(val feed: Feed, private val partial: Feed = feed) : DefaultParcelable {
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.write(partial.filter)
            dest.writeInt(ContentType.combine(partial.contentType))
            dest.writeBooleanCompat(partial.isAtStart)
            dest.write(partial.created)
            dest.writeValues(partial.items)
        }

        companion object CREATOR : SimpleCreator<FeedParcel>() {
            override fun createFromParcel(source: Parcel): FeedParcel = with(source) {
                return FeedParcel(feed = Feed(
                        filter = read(FeedFilter),
                        contentType = ContentType.decompose(readInt()),
                        isAtStart = readBooleanCompat(),
                        created = read(Instant),
                        items = readValues(FeedItem),
                ))
            }
        }
    }
}
