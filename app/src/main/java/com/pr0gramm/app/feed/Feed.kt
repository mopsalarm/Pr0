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

    val oldestNonPlaceholderItem: FeedItem? get() = items.asSequence().filterNot { item -> item.placeholder }.maxWithOrNull(itemComparator)
    val newestNonPlaceholderItem: FeedItem? get() = items.asSequence().filterNot { item -> item.placeholder }.minWithOrNull(itemComparator)

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

    fun withoutPlaceholderItems(): Feed {
        return copy(items = items.filterNot { item -> item.placeholder })
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

        // set with ids of all real values
        val realIds = target.filter { item -> !item.placeholder }.mapTo(mutableSetOf()) { item -> item.id }

        // remove all placeholders from the target that also have real ids.
        target.removeAll { item -> item.placeholder && item.id in realIds }

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
        return "Feed(newest=${newestNonPlaceholderItem?.id}, oldest=${oldestNonPlaceholderItem?.id}, size=$size, filter=$filter)"
    }

    fun parcelAroundId(itemId: Long): FeedParcel {
        val index = indexById(itemId) ?: 0
        return parcelAround(index)
    }

    fun parcelAround(index: Int): FeedParcel {
        // how many items to save to older and newer than the pivot item.
        val itemCountAround = 16

        // add a subset of the items
        val fromIndex = (index - itemCountAround).coerceIn(items.indices)
        val toIndexExclusive = (index + itemCountAround).coerceIn(0, items.size)

        // return a parcel with a window to the data
        return FeedParcel(this, items = copy(items = this.items.subList(fromIndex, toIndexExclusive)))
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

    class FeedParcel(val feed: Feed, private val items: List<FeedItem>) : DefaultParcelable {
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.write(feed.filter)
            dest.writeInt(ContentType.combine(feed.contentType))
            dest.writeBooleanCompat(feed.isAtStart)
            dest.write(feed.created)

            // only serialize non placeholder items
            dest.writeValues(items.filter { item -> !item.placeholder })

            val ids = LongArray(feed.size) { idx ->
                (feed[idx].id shl 32) or (feed[idx].promotedId)
            }

            // write id's of all values in the feed
            dest.writeLongArray(ids)
        }

        companion object CREATOR : SimpleCreator<FeedParcel>() {
            override fun createFromParcel(source: Parcel): FeedParcel = with(source) {
                val base = Feed(
                        filter = read(FeedFilter),
                        contentType = ContentType.decompose(readInt()),
                        isAtStart = readBooleanCompat(),
                        created = read(Instant),
                        items = listOf(),
                )

                // read the actual items that are parceled
                val realItems = readValues(FeedItem)
                val realItemsById = realItems.associateBy { item -> item.id }

                // read item ids of all items in the feed
                val idsArray = createLongArray()!!

                // merge items with itemIds and create placeholders if required
                val items = idsArray.map { mergedItemId ->
                    val itemId = mergedItemId ushr 32
                    val itemPromotedId = mergedItemId and 0xffffffff

                    realItemsById[itemId] ?: placeholderFeedItem(itemId, itemPromotedId)
                }

                return FeedParcel(base.copy(items = items), items)
            }
        }
    }
}
