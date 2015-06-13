package com.pr0gramm.app.feed;

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import com.google.common.base.Optional;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

import static com.google.common.base.Preconditions.checkNotNull;
import static rx.android.app.AppObservable.bindFragment;

/**
 * This class handles loading of feed data.
 */
public class FeedLoader {
    private final FeedService feedService;
    private final Feed feed;
    private final Binder binder;

    private Subscription subscription;

    public FeedLoader(@NonNull Binder binder, @NonNull FeedService feedService, @NonNull Feed feed) {
        this.feedService = checkNotNull(feedService, "feedService");
        this.feed = checkNotNull(feed, "feed");
        this.binder = checkNotNull(binder, "binder");
    }

    public Feed getFeed() {
        return feed;
    }

    public void restart() {
        restart(Optional.<Long>absent());
    }

    public void restart(Optional<Long> around) {
        stop();

        // clear old feed
        this.feed.clear();

        Observable<com.pr0gramm.app.api.pr0gramm.response.Feed> response;
        response = feedService.getFeedItems(feed.getFeedFilter(), feed.getContentType(),
                Optional.<Long>absent(), around);

        subscribeTo(response);
    }

    public void next() {
        Optional<FeedItem> oldest = feed.oldest();
        if (feed.isAtEnd() || isLoading() || !oldest.isPresent())
            return;

        subscribeTo(feedService.getFeedItems(
                feed.getFeedFilter(), feed.getContentType(),
                Optional.of(oldest.get().getId(feed.getFeedFilter().getFeedType())),
                Optional.<Long>absent()));
    }

    public void previous() {
        Optional<FeedItem> newest = feed.newest();
        if (feed.isAtStart() || isLoading() || !newest.isPresent())
            return;

        subscribeTo(feedService.getFeedItemsNewer(
                feed.getFeedFilter(), feed.getContentType(),
                newest.get().getId(feed.getFeedFilter().getFeedType())));
    }

    public boolean isLoading() {
        return subscription != null;
    }

    private void subscribeTo(Observable<com.pr0gramm.app.api.pr0gramm.response.Feed> response) {
        subscription = binder.bind(response)
                .finallyDo(() -> subscription = null)
                .subscribe(this::merge, binder::onError);
    }

    private void merge(com.pr0gramm.app.api.pr0gramm.response.Feed feed) {
        this.feed.merge(feed);
    }

    /**
     * Stops all loading operations.
     */
    public void stop() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    public interface Binder {
        /**
         * Bind the given observable to some kind of context like a fragment or thread.
         */
        <T> Observable<T> bind(Observable<T> observable);

        /**
         * Handles error responses while loading feed data
         */
        void onError(Throwable error);
    }

    public static Binder bindTo(Fragment fragment, Action1<Throwable> onError) {
        return new Binder() {
            @Override
            public <T> Observable<T> bind(Observable<T> observable) {
                return bindFragment(fragment, observable);
            }

            @Override
            public void onError(Throwable error) {
                onError.call(error);
            }
        };
    }
}
