package com.pr0gramm.app.feed

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.BackgroundScheduler
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject

class FeedManager(val feedService: FeedService, feed: Feed) {
    var feed: Feed = feed
        private set

    private val subject = BehaviorSubject.create<Update>(Update.NewFeed(feed)).toSerialized()
    private var subscription: Subscription? = null

    /**
     * True, if this feed manager is currently performing a load operation.
     */
    val isLoading: Boolean get() = subscription != null

    val feedType: FeedType get() = feed.feedType

    val updates: Observable<Update> get() = subject.startWith(Update.NewFeed(feed))

    /**
     * Stops all loading operations and resets the feed to the given value.
     */
    fun reset(feed: Feed = this.feed.copy(items = listOf(), isAtStart = false, isAtEnd = false)) {
        stop()
        publish(feed, remote = false)
    }

    /**
     * Resets the current feed and loads all items around the given offset.
     * Leave 'around' null to just load from the beginning.
     */
    fun restart(around: Long? = null) {
        stop()
        subscribeTo(feedService.getFeedItems(feedQuery().copy(around = around)))
    }

    /**
     * Load the next page of the feed
     */
    fun next() {
        val oldest = feed.oldest
        if (feed.isAtEnd || isLoading || oldest == null)
            return

        subscribeTo(feedService.getFeedItems(feedQuery().copy(older = oldest.id(feedType))))
    }

    /**
     * Load the previous page of the feed
     */
    fun previous() {
        val newest = feed.newest
        if (feed.isAtStart || isLoading || newest == null)
            return

        subscribeTo(feedService.getFeedItems(feedQuery().copy(newer = newest.id(feedType))))
    }

    fun stop() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun subscribeTo(observable: Observable<Api.Feed>) {
        subscription?.unsubscribe()
        subscription = observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .doOnSubscribe { subject.onNext(Update.LoadingStarted()) }
                .doOnUnsubscribe {
                    subject.onNext(Update.LoadingStopped())
                    subscription = null
                }
                .subscribe({ handleFeedUpdate(it) }, { publishError(it) })

    }

    private fun handleFeedUpdate(it: Api.Feed) {
        val merged = feed.mergeWith(it)
        publish(merged, remote = true)
    }

    private fun publishError(err: Throwable) {
        subject.onNext(Update.Error(err))
    }

    /**
     * Update and publish a new feed.
     */
    private fun publish(newFeed: Feed, remote: Boolean) {
        feed = newFeed
        subject.onNext(Update.NewFeed(newFeed, remote))
    }

    private fun feedQuery(): FeedService.FeedQuery {
        return FeedService.FeedQuery(feed.filter, feed.contentType)
    }

    sealed class Update {
        class LoadingStarted : Update()
        class LoadingStopped : Update()
        class Error(val err: Throwable) : Update()
        class NewFeed(val feed: Feed, val remote: Boolean = false) : Update()
    }
}