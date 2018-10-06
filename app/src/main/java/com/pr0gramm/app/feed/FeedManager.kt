package com.pr0gramm.app.feed

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.MainThreadScheduler
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.trace
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subjects.Subject
import rx.subscriptions.Subscriptions

class FeedManager(val feedService: FeedService, private var feed: Feed) {
    private val logger = logger("FeedService")

    private val subject: Subject<Update, Update> = BehaviorSubject.create<Update>().toSerialized()
    private var subscription: Subscription = Subscriptions.unsubscribed()

    /**
     * True, if this feed manager is currently performing a load operation.
     */
    val isLoading: Boolean get() = !subscription.isUnsubscribed

    private val feedType: FeedType get() = feed.feedType

    val updates: Observable<Update> get() = subject
            .observeOn(MainThreadScheduler)
            .startWith(Update.NewFeed(feed))

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
        subscribeTo(feedService.load(feedQuery().copy(around = around)))
    }

    /**
     * Load the next page of the feed
     */
    fun next() {
        val oldest = feed.oldest
        if (feed.isAtEnd || isLoading || oldest == null)
            return

        subscribeTo(feedService.load(feedQuery().copy(older = oldest.id(feedType))))
    }

    /**
     * Load the previous page of the feed
     */
    fun previous() {
        val newest = feed.newest
        if (feed.isAtStart || isLoading || newest == null)
            return

        subscribeTo(feedService.load(feedQuery().copy(newer = newest.id(feedType))))
    }

    fun stop() {
        trace { "stop" }
        subscription.unsubscribe()
    }

    private fun subscribeTo(observable: Observable<Api.Feed>) {
        logger.info("Subscribing to new load-request now.")

        subscription.unsubscribe()
        subscription = observable
                .subscribeOn(BackgroundScheduler)
                .unsubscribeOn(BackgroundScheduler)
                .doOnSubscribe { subject.onNext(Update.LoadingStarted) }
                .doOnUnsubscribe { subject.onNext(Update.LoadingStopped) }
                .subscribe({ handleFeedUpdate(it) }, { publishError(it) })

    }

    private fun handleFeedUpdate(update: Api.Feed) {
        // check for invalid content type.
        update.error?.let { error ->
            publishError(when (error) {
                "notPublic" -> FeedException.NotPublicException()
                "notFound" -> FeedException.NotFoundException()
                "sfwRequired" -> FeedException.InvalidContentTypeException(ContentType.SFW)
                "nsfwRequired" -> FeedException.InvalidContentTypeException(ContentType.NSFW)
                "nsflRequired" -> FeedException.InvalidContentTypeException(ContentType.NSFL)
                else -> FeedException.GeneralFeedException(error)
            })

            return
        }

        val merged = feed.mergeWith(update)
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
        object LoadingStarted : Update()
        object LoadingStopped : Update()
        data class Error(val err: Throwable) : Update()
        data class NewFeed(val feed: Feed, val remote: Boolean = false) : Update()
    }
}