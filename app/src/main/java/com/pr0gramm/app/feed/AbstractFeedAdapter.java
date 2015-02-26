package com.pr0gramm.app.feed;

import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.google.common.base.Optional;
import com.pr0gramm.app.api.Feed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;

/**
 */
public abstract class AbstractFeedAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    private final List<FeedItem> items = new ArrayList<>();

    private final FeedService feedService;
    private final Query query;

    private boolean loading;
    private boolean atEnd;
    private boolean atStart;

    public AbstractFeedAdapter(FeedService feedService, Query query,
                               Optional<Long> start) {

        this.feedService = feedService;
        this.query = query;
        this.atStart = !start.isPresent();

        setHasStableIds(true);
        loadAfter(start);
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
    protected <E> Observable<E> bind(Observable<E> observable) {
        return observable
                .finallyDo(this::onLoadFinished)
                .finallyDo(() -> loading = false);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId(query.getFeedType());
    }

    public List<FeedItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Returns the feed item at the given index.
     */
    public FeedItem getItem(int idx) {
        return items.get(idx);
    }

    public Query getQuery() {
        return query;
    }

    /**
     * Asynchronously loads the before-page before
     */
    public void loadPreviousPage() {
        if (loading || items.isEmpty() || isAtStart())
            return;

        loading = true;
        onLoadStart();

        // do the loading.
        long newest = items.get(0).getId(query.getFeedType());
        bind(feedService.getFeedItemsNewer(query, newest))
                .map(this::enhance)
                .subscribe(this::store, this::onError);
    }

    private EnhancedFeed enhance(Feed feed) {
        List<FeedItem> items = new ArrayList<>();
        for (Feed.Item item : feed.getItems())
            items.add(new FeedItem(item, false));

        return new EnhancedFeed(feed, items);
    }

    /**
     * Asynchronously loads the next page
     */
    public void loadNextPage() {
        if (loading || isAtEnd() || items.isEmpty())
            return;

        long oldest = items.get(items.size() - 1).getId(query.getFeedType());
        loadAfter(Optional.of(oldest));
    }

    /**
     * Loads one page of feed items after the given start post.
     *
     * @param start The post to start at.
     */
    private void loadAfter(Optional<Long> start) {
        if (loading)
            return;

        loading = true;
        onLoadStart();

        // do the loading.
        bind(feedService.getFeedItems(query, start))
                .map(this::enhance)
                .subscribe(this::store, this::onError);
    }

    /**
     * Clears this adapter and loads the first page of items again.
     */
    public void restart() {
        if (loading)
            return;

        // remove all previous items from the adapter.
        int oldSize = items.size();
        items.clear();
        notifyItemRangeRemoved(0, oldSize);

        // and start loading the first page
        atEnd = false;
        atStart = false;
        loadAfter(Optional.absent());
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

        List<Feed.Item> newItems = feed.getFeed().getItems();
        if (newItems.size() > 0) {
            // calculate where to insert
            int index = 0;
            if (!items.isEmpty() && newItems.get(0).getId() < items.get(0).getItem().getId())
                index = items.size();

            // insert and notify observers about changes
            items.addAll(index, feed.getFeedItems());
            notifyItemRangeInserted(index, newItems.size());
        }
    }

    protected void onError(Throwable error) {
        Log.e("Feed", "Error loading feed", error);
    }

    protected void onLoadStart() {
        Log.i("Feed", "loading started, item count: " + items.size());
    }

    protected void onLoadFinished() {
        Log.i("Feed", "loading finished, item count: " + items.size());
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
}
