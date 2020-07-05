package com.pr0gramm.app.feed

import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class FeedManager(private val scope: CoroutineScope, private val feedService: FeedService, private var feed: Feed) {
    private val logger = Logger("FeedService")

    private val subject = BroadcastChannel<Update>(8)
    private var job: Job? = null

    /**
     * True, if this feed manager is currently performing a load operation.
     */
    val isLoading: Boolean get() = job?.isActive == true

    private val feedType: FeedType get() = feed.feedType

    val updates: Flow<Update>
        get() = subject.asFlow().onStart { emit(Update.NewFeed(feed)) }

    /**
     * Stops all loading operations and resets the feed to the given value.
     */
    fun reset(feed: Feed = this.feed.copy(items = listOf(), isAtStart = false, isAtEnd = false)) {
        publish(feed, remote = false)
    }

    /**
     * Resets the current feed and loads all items around the given offset.
     * Leave 'around' null to just load from the beginning.
     */
    fun restart(around: Long? = null) {
        load {
            publish(Update.LoadingStarted(LoadingSpace.NEXT))
            feedService.load(feedQuery().copy(around = around))
        }
    }

    /**
     * Load the next page of the feed
     */
    fun next() {
        val oldest = feed.oldest
        if (feed.isAtEnd || isLoading || oldest == null)
            return

        load {
            publish(Update.LoadingStarted(LoadingSpace.NEXT))
            feedService.load(feedQuery().copy(older = oldest.id(feedType)))
        }
    }

    /**
     * Load the previous page of the feed
     */
    fun previous() {
        val newest = feed.newest
        if (feed.isAtStart || isLoading || newest == null)
            return

        load {
            publish(Update.LoadingStarted(LoadingSpace.PREV))
            feedService.load(feedQuery().copy(newer = newest.id(feedType)))
        }
    }

    fun stop() {
        trace { "stop" }

        job?.cancel()
    }

    private fun load(block: suspend () -> Api.Feed) {
        stop()

        logger.debug { "Start new load request now." }

        job = scope.launch {
            try {
                val update = try {
                    block()
                } catch (_: CancellationException) {
                    return@launch
                }

                // loading finished
                publish(Update.LoadingStopped)
                handleFeedUpdate(update)

            } catch (err: Throwable) {
                publish(Update.LoadingStopped)
                publishError(err)
            }
        }
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

    /**
     * Update and publish a new feed.
     */
    private fun publish(newFeed: Feed, remote: Boolean) {
        feed = newFeed
        publish(Update.NewFeed(newFeed, remote))
    }

    private fun publishError(err: Throwable) {
        subject.offer(Update.Error(err))
    }

    private fun publish(update: Update) {
        subject.offer(update)
    }

    private fun feedQuery(): FeedService.FeedQuery {
        return FeedService.FeedQuery(feed.filter, feed.contentType)
    }

    sealed class Update {
        object LoadingStopped : Update()
        data class LoadingStarted(val where: LoadingSpace = LoadingSpace.NEXT) : Update()
        data class Error(val err: Throwable) : Update()
        data class NewFeed(val feed: Feed, val remote: Boolean = false) : Update()
    }

    enum class LoadingSpace {
        NEXT, PREV
    }
}