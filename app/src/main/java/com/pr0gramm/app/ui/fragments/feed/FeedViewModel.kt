package com.pr0gramm.app.ui.fragments.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.SeenService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.fragments.CommentRef
import com.pr0gramm.app.util.*
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import kotlin.math.abs
import kotlin.time.seconds

class FeedViewModel(
        filter: FeedFilter, loadAroundItemId: Long?,

        private val feedService: FeedService,
        private val userService: UserService,
        private val seenService: SeenService,
        private val inMemoryCacheService: InMemoryCacheService,
        private val preloadManager: PreloadManager,
        private val adService: AdService
) : ViewModel() {

    private val logger = Logger("FeedViewModel")

    private val loader = FeedManager(viewModelScope, feedService, Feed(filter, userService.selectedContentType))

    val feedState = MutableStateFlow(FeedState(
            // correctly initialize the state
            feed = Feed(filter, userService.selectedContentType)
    ))

    init {
        viewModelScope.launch { observeFeedUpdates() }
        viewModelScope.launch { observeAdsVisibility() }
        viewModelScope.launch { observeSeenService() }
        viewModelScope.launch { observeSettings() }
        viewModelScope.launch { observePreloadState() }

        // start loading the feed
        loader.restart(around = loadAroundItemId)
    }

    private suspend fun observePreloadState() {
        preloadManager.items.sample(1.seconds).collect { items ->
            feedState.update { previousState -> previousState.copy(preloadedItemIds = items) }
        }
    }

    private suspend fun observeSettings() {
        Settings.changes { markItemsAsSeen }.collect { markAsSeen ->
            feedState.update { previousState -> previousState.copy(markItemsAsSeen = markAsSeen) }
        }
    }

    private suspend fun observeSeenService() {
        seenService.currentGeneration.collect {
            feedState.update { previousState ->
                val seen = previousState.feed.map { it.id }.filterTo(HashSet()) { id -> seenService.isSeen(id) }
                previousState.copy(seen = seen)
            }
        }
    }

    private suspend fun observeAdsVisibility() {
        adService.enabledForType(Config.AdType.FEED).collect { areVisible ->
            feedState.value = feedState.value.copy(adsVisible = areVisible)
        }
    }

    private suspend fun observeFeedUpdates() {
        var lastLoadingSpace: FeedManager.LoadingSpace? = null

        loader.updates.collect { update ->
            trace { "observeFeedUpdates($update)" }

            when (update) {
                is FeedManager.Update.NewFeed -> {
                    val currentState = feedState.value

                    viewModelScope.launch {
                        // update repost info in background
                        refreshRepostInfos(currentState.feed, update.feed)
                    }

                    var autoScrollRef: ConsumableValue<ScrollRef>? = null

                    if (lastLoadingSpace == FeedManager.LoadingSpace.PREV) {
                        val firstOldItem = currentState.feed.firstOrNull()
                        if (firstOldItem != null) {
                            autoScrollRef = ConsumableValue(ScrollRef(CommentRef(firstOldItem), keepScroll = true, smoothScroll = false))
                        }
                    }

                    val highlightedItemIds = withContext(Dispatchers.Default) {
                        logger.time("Updating highlighted item ids") {
                            findHighlightedItemIds(update.feed, currentState.highlightedItemIds)
                        }
                    }

                    feedState.update { previousState ->
                        previousState.copy(
                                feed = update.feed,
                                seen = update.feed.filter { item -> seenService.isSeen(item.id) }.mapTo(HashSet()) { it.id },
                                empty = update.remote && update.feed.isEmpty(),
                                highlightedItemIds = highlightedItemIds,
                                autoScrollRef = autoScrollRef,
                                error = null,
                                errorConsumable = null,
                                missingContentType = null,
                                loading = null,
                        )
                    }
                }

                is FeedManager.Update.Error -> {
                    val error = update.err

                    logger.error("Error loading the feed", error)

                    feedState.update { previousState ->
                        val baseState = previousState.copy(
                                error = null, errorConsumable = null, missingContentType = null,
                        )

                        when (error) {
                            is FeedException.InvalidContentTypeException -> {
                                baseState.copy(missingContentType = error.requiredType)
                            }

                            is FeedException -> {
                                baseState.copy(errorConsumable = ConsumableValue(error))
                            }

                            else -> {
                                val errorValue: Throwable = when {
                                    error is JsonEncodingException ->
                                        StringException(error, R.string.could_not_load_feed_json)

                                    error.rootCause is ConnectException ->
                                        StringException(error, R.string.could_not_load_feed_https)

                                    else -> error
                                }

                                baseState.copy(error = errorValue)
                            }
                        }
                    }
                }

                is FeedManager.Update.LoadingStarted -> {
                    lastLoadingSpace = update.where
                    feedState.value = feedState.value.copy(loading = update.where)
                }

                FeedManager.Update.LoadingStopped -> {
                    feedState.value = feedState.value.copy(loading = null)
                }
            }
        }
    }

    private fun findHighlightedItemIds(feed: Feed, highlightedItemIds: Set<Long>): Set<Long> {
        val allItemIds = feed.mapTo(HashSet()) { it.id }

        // list of items sorted by ascending points
        val topItems = feed.drop(10).dropLast(10)
                .filter { item -> isTopCandidate(item) }
                .sortedByDescending { it.up - it.down }

        // start with items that are actually still in the feed
        val result = highlightedItemIds.filterTo(HashSet()) { itemId -> itemId in allItemIds }

        // list of indices that are actually taken
        val indicesThatAreTaken = feed.mapIndexedNotNullTo(ArrayList()) { index, item ->
            index.takeIf { item.id in result }
        }

        outer@ while (result.size < feed.size / 50) {
            for (item in topItems) {
                // skip already highlighted items
                if (item.id in result) {
                    continue
                }

                val itemIndex = feed.indexOf(item)

                val isNearToAnyHighlightedItem = indicesThatAreTaken.any { takeIndex ->
                    abs(takeIndex - itemIndex) < 12
                }

                if (isNearToAnyHighlightedItem) {
                    continue
                }

                // looks good, lets take this item.
                result += item.id
                indicesThatAreTaken += itemIndex

                continue@outer
            }

            // we did not find a place for any item, give up now.
            break
        }

        return result
    }

    private fun isTopCandidate(item: FeedItem): Boolean {
        // only items with an aspect ratio of more than than 3/2 are candidates.
        if (item.width.toDouble() / item.height < 3.0 / 2.0) {
            return false
        }

        // only images & videos are candidates right now.
        return item.isImage || item.isVideo
    }

    private suspend fun refreshRepostInfos(old: Feed, new: Feed) {
        trace { "refreshRepostInfos" }

        val filter = new.filter
        if (filter.feedType !== FeedType.NEW && filter.feedType !== FeedType.PROMOTED)
            return

        // check if query is too long to actually get the info info
        val queryIsTooLong = filter.tags?.split(Regex("\\s+"))?.count { it.isNotEmpty() }
        if (queryIsTooLong != null && queryIsTooLong >= 5)
            return

        // get the most recent item in the updated feed items
        val newestItem = (new - old).filter { !it.isPinned }.maxBy { new.feedTypeId(it) } ?: return

        // add 'repost' to query
        val queryTerm = Tags.join("! 'repost'", filter.tags)

        // load repost info for the new items, starting at the most recent one
        val query = FeedService.FeedQuery(
                filter = filter.withTags(queryTerm),
                contentTypes = new.contentType,
                older = new.feedTypeId(newestItem)
        )

        if (inMemoryCacheService.refreshRepostsCache(feedService, query)) {
            logger.debug { "Repost info was refreshed, updating state now" }
            feedState.value = feedState.value.copy(repostRefreshTime = System.currentTimeMillis())
        }
    }

    fun replaceCurrentFeed(feed: Feed) {
        feedState.update { previousState -> previousState.copy(feed = feed) }
    }

    fun restart(feed: Feed, aroundItemId: Long?) {
        loader.reset(feed)
        loader.restart(around = aroundItemId)
    }

    fun refresh() {
        loader.reset()
        loader.restart()
    }

    fun loaderNext() {
        feedState.update { previousState ->
            previousState.copy(loading = FeedManager.LoadingSpace.NEXT)
        }

        loader.next()
    }

    fun loaderPrevious() {
        feedState.update { previousState ->
            previousState.copy(loading = FeedManager.LoadingSpace.PREV)
        }

        loader.previous()
    }

    data class FeedState(
            val feed: Feed,
            val seen: Set<Long> = setOf(),
            val errorStr: String? = null,
            val error: Throwable? = null,
            val errorConsumable: ConsumableValue<Throwable>? = null,
            val missingContentType: ContentType? = null,
            val loading: FeedManager.LoadingSpace? = null,
            val repostRefreshTime: Long = 0,
            val adsVisible: Boolean = false,
            val markItemsAsSeen: Boolean = Settings.get().markItemsAsSeen,
            val preloadedItemIds: LongSparseArray<PreloadManager.PreloadItem> = LongSparseArray(initialCapacity = 0),
            val autoScrollRef: ConsumableValue<ScrollRef>? = null,
            val highlightedItemIds: Set<Long> = setOf(),
            val empty: Boolean = false
    ) {
        val isLoading = loading != null
    }
}

inline fun <T> MutableStateFlow<T>.update(block: (previousState: T) -> T) {
    value = block(value)
}

inline fun <T> LazyStateFlow<T>.update(block: (previousState: T) -> T) {
    send(block(value))
}

