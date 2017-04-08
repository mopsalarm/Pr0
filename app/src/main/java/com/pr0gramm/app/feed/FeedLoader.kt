package com.pr0gramm.app.feed

import com.pr0gramm.app.api.pr0gramm.Api
import rx.Observable
import rx.Subscription
import rx.functions.Action1

/**
 * This class handles loading of feed data.
 */
class FeedLoader(val binder: FeedLoader.Binder, val feedService: FeedService, val feed: Feed) {
    @Volatile
    private var subscription: Subscription? = null

    fun restart(around: Long?) {
        stop()

        // clear old feed
        this.feed.clear()

        subscribeTo(feedService.getFeedItems(newQuery().copy(around = around)))
    }

    fun next() {
        val oldest = feed.oldest()
        if (feed.isAtEnd || isLoading || !oldest.isPresent)
            return

        subscribeTo(feedService.getFeedItems(newQuery().copy(older = oldest.get().getId(feed.feedFilter.feedType))))
    }

    fun previous() {
        val newest = feed.newest()
        if (feed.isAtStart || isLoading || !newest.isPresent)
            return

        subscribeTo(feedService.getFeedItems(newQuery().copy(newer = newest.get().getId(feed.feedFilter.feedType))))
    }

    val isLoading: Boolean
        get() = subscription != null

    private fun subscribeTo(response: Observable<Api.Feed>) {
        subscription = response
                .compose(binder.bind())
                .doAfterTerminate { subscription = null }
                .subscribe({ this.merge(it) }, { binder.onError(it) })
    }

    private fun newQuery(): FeedService.FeedQuery {
        return FeedService.FeedQuery(feed.feedFilter, feed.contentType)
    }

    private fun merge(feed: Api.Feed) {
        this.feed.merge(feed)
    }

    /**
     * Stops all loading operations.
     */
    private fun stop() {
        if (subscription != null) {
            subscription!!.unsubscribe()
            subscription = null
        }
    }

    interface Binder {
        /**
         * Bind the given observable to some kind of context like a fragment or thread.
         */
        fun <T> bind(): Observable.Transformer<T, T>

        /**
         * Handles error responses while loading feed data
         */
        fun onError(error: Throwable)
    }

    companion object {
        fun bindTo(transformer: Observable.Transformer<*, *>, onError: Action1<Throwable>): Binder {
            return object : Binder {
                override fun <T> bind(): Observable.Transformer<T, T> {
                    return transformer as Observable.Transformer<T, T>
                }

                override fun onError(error: Throwable) {
                    onError.call(error)
                }
            }
        }
    }
}
