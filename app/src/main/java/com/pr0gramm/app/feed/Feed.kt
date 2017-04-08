package com.pr0gramm.app.feed

import android.os.Bundle
import com.google.common.base.Optional
import com.google.common.collect.Iterators
import com.google.common.collect.Ordering
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.filter
import com.pr0gramm.app.util.map
import org.slf4j.LoggerFactory
import java.lang.Math.max
import java.lang.ref.WeakReference
import java.util.*

/**
 * Represents a feed.
 */
class Feed(val feedFilter: FeedFilter, contentType: Set<ContentType>) {

    private val items = ArrayList<FeedItem>()

    private val itemComparator = compareByDescending(this::feedTypeId)

    val contentType: Set<ContentType> = EnumSet.copyOf(contentType)

    var isAtEnd: Boolean = false
        private set

    var isAtStart: Boolean = false
        private set

    @Transient
    private var feedListener: WeakReference<FeedListener?> = WeakReference(null)

    /**
     * Constructs a new feed containing the given items.
     */
    constructor(feedFilter: FeedFilter,
                contentType: Set<ContentType>,
                items: Collection<FeedItem>,
                atStart: Boolean) : this(feedFilter, contentType) {

        this.items.addAll(items)
        this.isAtStart = atStart
    }

    fun getItems(): List<FeedItem> {
        return Collections.unmodifiableList(items)
    }

    fun at(idx: Int): FeedItem {
        return items[idx]
    }

    fun size(): Int {
        return items.size
    }

    /**
     * Sets the given listener for this instance. The listener will be notified
     * about changes in the feed.
     */
    fun setFeedListener(feedListener: FeedListener) {
        this.feedListener = WeakReference(feedListener)
    }

    fun clear() {
        this.items.clear()
        dispatchEvent { it.onRemoveItems() }
    }

    /**
     * Merges this feed with the provided low level feed representation.
     */
    fun merge(feed: Api.Feed) {
        checkMainThread()

        isAtEnd = isAtEnd or feed.isAtEnd
        isAtStart = isAtStart or feed.isAtStart

        val newItems = add(feed)

        if (!Ordering.from(itemComparator).isStrictlyOrdered(items)) {
            logger.warn("Feed is not in order after merging!")
        }

        dispatchEvent { listener ->
            listener.onNewItems(newItems)

            val wrongContentType = feed.error
                    .map { error -> error.endsWith("Required") }
                    .or(false)

            if (wrongContentType) {
                listener.onWrongContentType()
            }
        }
    }

    /**
     * Adds the items from the provided feed to this instance.
     */
    private fun add(feed: Api.Feed): List<FeedItem> {
        val newItems = feed.items.mapTo(mutableListOf(), ::FeedItem)

        // we can not merge some feeds based on their ids
        if (!feedFilter.feedType.sortable) {
            items.addAll(newItems)

        } else {
            newItems.sortByDescending { feedTypeId(it) }

            // merge based on ids.
            val source = Iterators.peekingIterator(newItems.iterator())
            val target = items.listIterator()

            while (source.hasNext()) {
                if (target.hasNext()) {
                    val nextTarget = items[target.nextIndex()]

                    val cmp = itemComparator.compare(source.peek(), nextTarget)
                    if (cmp < 0) {
                        // next target should belong behind this source item, so
                        // put source item here.
                        target.add(source.next())

                    } else if (cmp == 0) {
                        // replace target with new source
                        target.next()
                        target.set(source.next())

                    } else {
                        // don't insert here, try next target
                        target.next()
                    }

                } else {
                    // we have no more target elements, so just add source here.
                    target.add(source.next())
                }
            }
        }

        return newItems
    }

    private fun feedTypeId(item: FeedItem): Long {
        return item.getId(feedFilter.feedType)
    }

    /**
     * Returns the index of the given feed item in this feed. If the iem
     * is not part of this feed, an empty optional will be returned.
     */
    fun indexOf(item: FeedItem?): Optional<Int> {
        if (item == null)
            return Optional.absent<Int>()

        return indexOf(item.id())
    }

    fun indexOf(itemId: Long): Optional<Int> {
        return Optional.of(items.indexOfFirst { it.id() == itemId }).filter { it >= 0 }
    }

    fun persist(idx: Int): Bundle {
        val bundle = Bundle()
        bundle.putParcelable(FEED_FIELD_FILTER, feedFilter)
        bundle.putInt(FEED_FIELD_CONTENT_TYPE, ContentType.combine(contentType))

        // add a subset of the items
        val start = Math.min(items.size, max(0, idx - 64))
        val stop = Math.min(items.size, max(0, idx + 64))
        val items = this.items.subList(start, stop)
        bundle.putParcelableArrayList(FEED_FIELD_ITEMS, ArrayList(items))
        bundle.putBoolean(FEED_FIELD_AT_START, start == 0)

        return bundle
    }

    fun oldest(): Optional<FeedItem> {
        return Optional.fromNullable(items.maxWith(itemComparator))
    }

    fun newest(): Optional<FeedItem> {
        return Optional.fromNullable(items.minWith(itemComparator))
    }

    private fun dispatchEvent(action: (FeedListener) -> Unit) {
        this.feedListener.get()?.let { action(it) }
    }

    interface FeedListener {
        /**
         * Called after new items are added to this feed.
         */
        fun onNewItems(newItems: List<FeedItem>)

        /**
         * Called if items are removed from the feed
         */
        fun onRemoveItems()

        /**
         * This will be called if we try to get a nsfw image with a sfw feed.
         */
        fun onWrongContentType()
    }

    companion object {
        private val logger = LoggerFactory.getLogger("Feed")

        val FEED_FIELD_FILTER = "filter"
        val FEED_FIELD_ITEMS = "items"
        val FEED_FIELD_CONTENT_TYPE = "contentType"
        val FEED_FIELD_AT_START = "atStart"

        fun restore(bundle: Bundle): Feed {
            val feedFilter = bundle.getParcelable<FeedFilter>(FEED_FIELD_FILTER)
            val items = bundle.getParcelableArrayList<FeedItem>(FEED_FIELD_ITEMS)
            val contentType = ContentType.decompose(bundle.getInt(FEED_FIELD_CONTENT_TYPE))
            val atStart = bundle.getBoolean(FEED_FIELD_AT_START, false)

            return Feed(feedFilter, contentType, items, atStart)
        }
    }
}
