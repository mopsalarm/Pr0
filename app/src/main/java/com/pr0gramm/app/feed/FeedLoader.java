package com.pr0gramm.app.feed;

import android.support.annotation.NonNull;

import com.google.common.base.Optional;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.trello.rxlifecycle.FragmentLifecycleProvider;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

import static com.google.common.base.Preconditions.checkNotNull;

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
        response = feedService.getFeedItems(ImmutableFeedQuery.builder()
                .feedFilter(feed.getFeedFilter())
                .contentTypes(feed.getContentType())
                .around(around)
                .build());

        subscribeTo(response);
    }

    public void next() {
        Optional<FeedItem> oldest = feed.oldest();
        if (feed.isAtEnd() || isLoading() || !oldest.isPresent())
            return;

        subscribeTo(feedService.getFeedItems(ImmutableFeedQuery.builder()
                .feedFilter(feed.getFeedFilter())
                .contentTypes(feed.getContentType())
                .older(oldest.get().getId(feed.getFeedFilter().getFeedType()))
                .build()));
    }

    public void previous() {
        Optional<FeedItem> newest = feed.newest();
        if (feed.isAtStart() || isLoading() || !newest.isPresent())
            return;

        subscribeTo(feedService.getFeedItems(ImmutableFeedQuery.builder()
                .feedFilter(feed.getFeedFilter())
                .contentTypes(feed.getContentType())
                .newer(newest.get().getId(feed.getFeedFilter().getFeedType()))
                .build()));
    }

    public boolean isLoading() {
        return subscription != null;
    }

    private void subscribeTo(Observable<com.pr0gramm.app.api.pr0gramm.response.Feed> response) {
        subscription = response
                .unsubscribeOn(BackgroundScheduler.instance())
                .compose(binder.bind())
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
        <T> Observable.Transformer<T, T> bind();

        /**
         * Handles error responses while loading feed data
         */
        void onError(Throwable error);
    }

    public static Binder bindTo(FragmentLifecycleProvider lifecycle, Action1<Throwable> onError) {
        Observable.Transformer transformer = lifecycle.bindToLifecycle();

        return new Binder() {
            @Override
            public <T> Observable.Transformer<T, T> bind() {
                //noinspection unchecked
                return transformer;
            }

            @Override
            public void onError(Throwable error) {
                onError.call(error);
            }
        };
    }
}
