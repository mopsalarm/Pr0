package com.pr0gramm.app.feed;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.google.common.base.Optional;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.api.pr0gramm.response.Feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.toArray;
import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class FeedProxy {
    private static final Logger logger = LoggerFactory.getLogger(FeedProxy.class);

    private final List<FeedItem> items = new ArrayList<>();

    private final FeedFilter feedFilter;
    private boolean loading;
    private boolean atEnd;
    private boolean atStart;

    @Nullable
    private transient OnChangeListener onChangeListener;

    @Nullable
    private transient Loader loader;

    public FeedProxy(FeedFilter feedFilter) {
        this.feedFilter = feedFilter;
    }

    private FeedProxy(FeedFilter feedFilter, List<FeedItem> items) {
        this.feedFilter = feedFilter;
        this.items.addAll(items);

        checkFeedOrder();
    }

    /**
     * Binds the observable to some kind of context.
     * Use {@link rx.android.observables.AndroidObservable#bindActivity(android.app.Activity, rx.Observable)}
     * or {@link rx.android.observables.AndroidObservable#bindFragment(Object, rx.Observable)} on
     * the given observable.
     *
     * @param observable The observable to bind.
     * @return The bound observable.
     */
    private <E> Observable<E> bind(Observable<E> observable) {
        if (loader == null) {
            observable = Observable.empty();
        } else {
            observable = loader.bind(observable);
        }

        return observable
                .finallyDo(this::onLoadFinished)
                .finallyDo(() -> loading = false);
    }

    /**
     * Returns a list of all the items in this feed.
     *
     * @return A list of all items in the feed.
     */
    public List<FeedItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Returns the feed item at the given index.
     */
    public FeedItem getItemAt(int idx) {
        return items.get(idx);
    }

    /**
     * Returns the query that is used to build this feed.
     *
     * @return A query that is used to build the feed
     */
    public FeedFilter getFeedFilter() {
        return feedFilter;
    }

    /**
     * Asynchronously loads the before-page before
     */
    public void loadPreviousPage() {
        if (loading || items.isEmpty() || isAtStart() || loader == null)
            return;

        loading = true;
        onLoadStart();

        // do the loading.
        long newest = items.get(0).getId(feedFilter.getFeedType());
        bind(loader.getFeedService().getFeedItemsNewer(feedFilter, newest))
                .map(this::enhance)
                .subscribe(this::store, this::onError);
    }

    /**
     * Enhances the feed by additional data.
     *
     * @param feed The feed to enhance
     * @return The enhanced feed to display
     */
    private EnhancedFeed enhance(Feed feed) {
        List<FeedItem> items = new ArrayList<>();
        for (Feed.Item item : feed.getItems()) {
            items.add(new FeedItem(item));
        }

        return new EnhancedFeed(feed, items);
    }

    /**
     * Asynchronously loads the next page
     */
    public void loadNextPage() {
        if (loading || isAtEnd() || items.isEmpty())
            return;

        long oldest = items.get(items.size() - 1).getId(feedFilter.getFeedType());
        load(Optional.of(oldest), Optional.absent());
    }

    /**
     * Loads one page of feed items after the given start post.
     *
     * @param start  The post to start at.
     * @param around The id to load around
     */
    private void load(Optional<Long> start, Optional<Long> around) {
        if (loading || loader == null)
            return;

        loading = true;
        onLoadStart();

        // do the loading.
        bind(loader.getFeedService().getFeedItems(feedFilter, start, around))
                .map(this::enhance)
                .subscribe(this::store, this::onError);
    }

    public void restart(Optional<Long> around) {
        if (loading) {
            logger.warn("Can not restart, currently loading");
            return;
        }

        // remove all previous items from the adapter.
        int oldSize = items.size();
        items.clear();
        if (onChangeListener != null)
            onChangeListener.onItemRangeRemoved(0, oldSize);

        // and start loading the next page
        atEnd = false;
        atStart = !around.isPresent();
        load(Optional.<Long>absent(), around);
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isAtStart() {
        return atStart;
    }

    public boolean isAtEnd() {
        return atEnd;
    }

    private void store(EnhancedFeed feed) {
        if (feed.getFeed().isAtEnd())
            atEnd = true;

        if (feed.getFeed().isAtStart())
            atStart = true;

        Ordering<FeedItem> ordering = Ordering.natural().reverse().onResultOf(this::feedTypeId);
        List<FeedItem> newItems = ordering.sortedCopy(feed.getFeedItems());

        if (newItems.size() > 0) {
            // calculate where to insert
            int index = 0;

            // get the maximum and the minimum id
            long newMaxId = feedTypeId(newItems.get(0));
            long newMinId = feedTypeId(getLast(newItems));

            if (!items.isEmpty()) {
                long oldMaxId = items.get(0).getId(feedFilter.getFeedType());
                long oldMinId = items.get(items.size() - 1).getId(feedFilter.getFeedType());

                if (newMinId > oldMaxId) {
                    logger.info("Okay, prepending new data to stored feed");
                    index = 0;

                } else if (newMaxId < oldMinId) {
                    logger.info("Okay, appending new data to stored feed");
                    index = items.size();

                } else if (newMinId < oldMinId) {
                    // mixed!
                    logger.warn("New data is overlapping with old data! Appending new data.");
                    index = items.size();

                } else if (newMaxId > oldMaxId) {
                    logger.warn("New data is overlapping with old data! Prepending new data.");
                    index = items.size();
                }
            }

            // insert and notify observers about changes
            items.addAll(index, newItems);

            if (onChangeListener != null)
                onChangeListener.onItemRangeInserted(index, newItems.size());
        }

        checkFeedOrder();
    }

    private void checkFeedOrder() {
        boolean ordered = Ordering.natural()
                .onResultOf((FeedItem item) -> item.getId(feedFilter.getFeedType()))
                .reverse()
                .isStrictlyOrdered(items);

        if (!ordered) {
            logger.warn("Feed not strictly ordered :/");
        }
    }

    private long feedTypeId(FeedItem item) {
        return item.getId(feedFilter.getFeedType());
    }

    protected void onError(Throwable error) {
        if (loader != null)
            loader.onError(error);
    }

    protected void onLoadStart() {
        if (loader != null)
            loader.onLoadStart();
    }

    protected void onLoadFinished() {
        if (loader != null)
            loader.onLoadFinished();
    }

    public void setOnChangeListener(@Nullable OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    public void setLoader(@Nullable Loader loader) {
        this.loader = loader;
    }

    public int getItemCount() {
        return items.size();
    }

    public Bundle toBundle(int idx) {
        Bundle bundle = new Bundle();
        bundle.putParcelable("query", feedFilter);

        // add a subset of the items
        int start = min(items.size(), max(0, idx - 32));
        int stop = min(items.size(), max(0, idx + 32));
        List<FeedItem> items = this.items.subList(start, stop);
        bundle.putParcelableArray("items", toArray(items, FeedItem.class));

        return bundle;
    }

    @SuppressWarnings("unchecked")
    public static FeedProxy fromBundle(Bundle bundle) {
        FeedFilter feedFilter = bundle.getParcelable("query");
        List<FeedItem> items = (List<FeedItem>) (List) asList(bundle.getParcelableArray("items"));
        return new FeedProxy(feedFilter, items);
    }

    public Optional<Integer> getPosition(@Nullable FeedItem item) {
        if (item == null)
            return Optional.absent();

        for (int idx = 0; idx < items.size(); idx++) {
            if (item.getId() == items.get(idx).getId())
                return Optional.of(idx);
        }

        return Optional.absent();
    }

    /**
     * Feed enhanced by {@link com.pr0gramm.app.feed.FeedItem}s.
     */
    private static class EnhancedFeed {
        private final Feed feed;
        private final List<FeedItem> feedItems;

        private EnhancedFeed(Feed feed, List<FeedItem> feedItems) {
            this.feed = feed;
            this.feedItems = feedItems;
        }

        public Feed getFeed() {
            return feed;
        }

        public List<FeedItem> getFeedItems() {
            return feedItems;
        }
    }

    public interface OnChangeListener {
        void onItemRangeInserted(int start, int count);

        void onItemRangeRemoved(int start, int count);
    }

    public interface Loader {
        <T> Observable<T> bind(Observable<T> observable);

        void onLoadStart();

        void onLoadFinished();

        void onError(Throwable error);

        FeedService getFeedService();
    }

    public static class FragmentFeedLoader implements Loader {
        private final Fragment fragment;
        private final FeedService feedService;

        public FragmentFeedLoader(Fragment fragment, FeedService feedService) {
            this.fragment = fragment;
            this.feedService = feedService;
        }

        @Override
        public <T> Observable<T> bind(Observable<T> observable) {
            return bindFragment(fragment, observable);
        }

        @Override
        public void onLoadStart() {

        }

        @Override
        public void onLoadFinished() {

        }

        @Override
        public void onError(Throwable error) {
            checkMainThread();
            logger.error("Could not load feed", error);
        }

        @Override
        public FeedService getFeedService() {
            return feedService;
        }
    }
}
