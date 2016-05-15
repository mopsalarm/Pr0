package com.pr0gramm.app.feed;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;
import com.google.common.collect.PeekingIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import rx.functions.Action1;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;
import static java.lang.Math.max;

/**
 * Represents a feed.
 */
public class Feed {
    private static final Logger logger = LoggerFactory.getLogger("Feed");
    public static final String FEED_FIELD_FILTER = "filter";
    public static final String FEED_FIELD_ITEMS = "items";
    public static final String FEED_FIELD_CONTENT_TYPE = "contentType";
    public static final String FEED_FIELD_AT_START = "atStart";

    private final List<FeedItem> items = new ArrayList<>();

    private final FeedFilter feedFilter;
    private final ImmutableSet<ContentType> contentType;

    private boolean atEnd;
    private boolean atStart;

    private transient WeakReference<FeedListener> feedListener;

    /**
     * Constructs a new and empty feed
     */
    public Feed(FeedFilter feedFilter, Set<ContentType> contentType) {
        this.feedFilter = checkNotNull(feedFilter, "feedFilter");
        this.contentType = ImmutableSet.copyOf(contentType);
    }

    /**
     * Constructs a new feed containing the given items.
     */
    public Feed(FeedFilter feedFilter, Set<ContentType> contentType, Collection<FeedItem> items,
                boolean atStart) {

        this(feedFilter, contentType);
        this.items.addAll(items);
        this.atStart = atStart;
    }

    public boolean isAtStart() {
        return atStart;
    }

    public boolean isAtEnd() {
        return atEnd;
    }

    public List<FeedItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public ImmutableSet<ContentType> getContentType() {
        return contentType;
    }

    public FeedFilter getFeedFilter() {
        return feedFilter;
    }

    public FeedItem at(int idx) {
        return items.get(idx);
    }

    public int size() {
        return items.size();
    }

    /**
     * Sets the given listener for this instance. The listener will be notified
     * about changes in the feed.
     */
    public void setFeedListener(FeedListener feedListener) {
        this.feedListener = new WeakReference<>(feedListener);
    }

    public void clear() {
        this.items.clear();
        event(FeedListener::onRemoveItems);
    }

    /**
     * Merges this feed with the provided low level feed representation.
     */
    public void merge(com.pr0gramm.app.api.pr0gramm.response.Feed feed) {
        checkMainThread();

        atEnd |= feed.isAtEnd();
        atStart |= feed.isAtStart();

        List<FeedItem> newItems = add(feed);

        if (!itemOrdering.isStrictlyOrdered(items)) {
            logger.warn("Feed is not in order after merging!");
        }

        if (feedListener != null) {
            boolean wrongContentType = feed.getError()
                    .transform(error -> error.endsWith("Required"))
                    .or(false);

            event(listener -> {
                listener.onNewItems(newItems);

                if (wrongContentType)
                    listener.onWrongContentType();
            });
        }
    }

    /**
     * Adds the items from the provided feed to this instance.
     */
    private List<FeedItem> add(com.pr0gramm.app.api.pr0gramm.response.Feed feed) {
        ImmutableList<FeedItem> newItems = FluentIterable.from(feed.getItems())
                .transform(FeedItem::new)
                .toList();

        // we can not merge some feeds based on their ids
        if (!feedFilter.getFeedType().sortable()) {
            items.addAll(newItems);

        } else {
            newItems = itemOrdering.immutableSortedCopy(newItems);

            // merge based on ids.
            PeekingIterator<FeedItem> source = Iterators.peekingIterator(newItems.iterator());
            ListIterator<FeedItem> target = items.listIterator();

            while (source.hasNext()) {
                if (target.hasNext()) {
                    FeedItem nextTarget = items.get(target.nextIndex());

                    int cmp = itemOrdering.compare(source.peek(), nextTarget);
                    if (cmp < 0) {
                        // next target should belong behind this source item, so
                        // put source item here.
                        target.add(source.next());

                    } else if (cmp == 0) {
                        // replace target with new source
                        target.next();
                        target.set(source.next());

                    } else {
                        // don't insert here, try next target
                        target.next();
                    }

                } else {
                    // we have no more target elements, so just add source here.
                    target.add(source.next());
                }
            }
        }

        return newItems;
    }

    private long feedTypeId(FeedItem item) {
        return item.id(feedFilter.getFeedType());
    }

    /**
     * Returns the index of the given feed item in this feed. If the iem
     * is not part of this feed, an empty optional will be returned.
     */
    public Optional<Integer> indexOf(@Nullable FeedItem item) {
        if (item == null)
            return Optional.absent();

        return indexOf(item.id());
    }

    public Optional<Integer> indexOf(long itemId) {
        for (int idx = 0; idx < items.size(); idx++) {
            if (itemId == items.get(idx).id()) {
                return Optional.of(idx);
            }
        }

        return Optional.absent();
    }

    public Bundle persist(int idx) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(FEED_FIELD_FILTER, feedFilter);
        bundle.putInt(FEED_FIELD_CONTENT_TYPE, ContentType.combine(contentType));

        // add a subset of the items
        int start = Math.min(items.size(), max(0, idx - 100));
        int stop = Math.min(items.size(), max(0, idx + 100));
        List<FeedItem> items = this.items.subList(start, stop);
        bundle.putParcelableArrayList(FEED_FIELD_ITEMS, new ArrayList<>(items));
        bundle.putBoolean(FEED_FIELD_AT_START, start == 0);

        return bundle;
    }

    @SuppressWarnings("unchecked")
    public static Feed restore(Bundle bundle) {
        FeedFilter feedFilter = bundle.getParcelable(FEED_FIELD_FILTER);
        List<FeedItem> items = bundle.getParcelableArrayList(FEED_FIELD_ITEMS);
        Set<ContentType> contentType = ContentType.decompose(bundle.getInt(FEED_FIELD_CONTENT_TYPE));
        boolean atStart = bundle.getBoolean(FEED_FIELD_AT_START, false);

        return new Feed(feedFilter, contentType, items, atStart);
    }

    private final Ordering<FeedItem> itemOrdering = Ordering
            .natural().reverse()
            .onResultOf(this::feedTypeId);

    public Optional<FeedItem> oldest() {
        return items.isEmpty()
                ? Optional.<FeedItem>absent()
                : Optional.of(itemOrdering.max(items));
    }

    public Optional<FeedItem> newest() {
        return items.isEmpty()
                ? Optional.<FeedItem>absent()
                : Optional.of(itemOrdering.min(items));
    }

    private void event(Action1<FeedListener> action) {
        if (feedListener != null) {
            FeedListener listener = this.feedListener.get();
            if (listener != null) {
                action.call(listener);
            }
        }
    }

    public interface FeedListener {
        /**
         * Called after new items are added to this feed.
         */
        void onNewItems(List<FeedItem> newItems);

        /**
         * Called if items are removed from the feed
         */
        void onRemoveItems();

        /**
         * This will be called if we try to get a nsfw image with a sfw feed.
         */
        void onWrongContentType();
    }
}
