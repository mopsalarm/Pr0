package com.pr0gramm.app.feed;

import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;

/**
 */
public abstract class AbstractFeedAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {
    private final List<FeedItem> items = new ArrayList<>();

    private final FeedService feedService;
    private final FeedService.Query query;

    private boolean loading;

    public AbstractFeedAdapter(FeedService feedService, FeedService.Query query) {

        this.feedService = feedService;
        this.query = query;

        setHasStableIds(true);
        loadAfter(query.getStart());
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
    protected abstract <E> Observable<E> bind(Observable<E> observable);

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
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

    public FeedService.Query getQuery() {
        return query;
    }

    /**
     * Asynchronously loads the next page
     */
    public void loadNextPage() {
        Optional<FeedItem> oldest = items.isEmpty()
                ? Optional.absent()
                : Optional.of(items.get(items.size() - 1));

        loadAfter(oldest);
    }

    /**
     * Loads one page of feed items after the given start post.
     *
     * @param start The post to start at.
     */
    private void loadAfter(Optional<FeedItem> start) {
        if (loading)
            return;

        loading = true;
        onLoadStart();

        // do the loading.
        bind(feedService.getFeedItems(query.withStart(start)))
                .finallyDo(this::onLoadFinished)
                .finallyDo(() -> loading = false)
                .subscribe(this::append, this::onError);
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
        loadAfter(Optional.<FeedItem>absent());
    }

    public boolean isLoading() {
        return loading;
    }

    private void append(List<FeedItem> newItems) {
        int oldCount = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(oldCount, newItems.size());
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
}
