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
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.fragments.CommentRef
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.StringException
import com.pr0gramm.app.util.rootCause
import com.pr0gramm.app.util.trace
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.net.ConnectException
import kotlin.time.milliseconds
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

    val loader = FeedManager(viewModelScope, feedService, Feed(filter, userService.selectedContentType))
    val feedState = MutableStateFlow(FeedState())

    private val taskQueue = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { observeFeedUpdates() }
        viewModelScope.launch { observeAdsVisibility() }
        viewModelScope.launch { observeSeenService() }
        viewModelScope.launch { observeSettings() }
        viewModelScope.launch { observePreloadState() }

        viewModelScope.launch { consumeTaskQueue() }

        // start loading the feed
        loader.restart(around = loadAroundItemId)
    }

    fun schedule(block: () -> Unit) {
        taskQueue.offer(block)
    }

    private suspend fun consumeTaskQueue() {
        trace { "Consume taskQueue $taskQueue" }
        try {
            taskQueue.consumeAsFlow().sample(250.milliseconds).collect { task -> task() }
        } finally {
            trace { "Stop consume taskQueue $taskQueue" }
        }
    }

    private suspend fun observePreloadState() {
        preloadManager.items.sample(1.seconds).collect { items ->
            feedState.update { copy(preloadedItemIds = items) }
        }
    }

    private suspend fun observeSettings() {
        Settings.changes { markItemsAsSeen }.collect { markAsSeen ->
            feedState.update { copy(markItemsAsSeen = markAsSeen) }
        }
    }

    private suspend fun observeSeenService() {
        seenService.currentGeneration.collect {
            feedState.update {
                copy(seen = feed
                        .filter { seenService.isSeen(it.id) }
                        .mapTo(HashSet()) { it.id })
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
            trace { "gotFeedUpdate($update)" }

            when (update) {
                is FeedManager.Update.NewFeed -> {
                    viewModelScope.launch {
                        // update repost info in background
                        refreshRepostInfos(feedState.value.feed, update.feed)
                    }

                    var autoScrollRef: ConsumableValue<ScrollRef>? = null

                    if (lastLoadingSpace == FeedManager.LoadingSpace.PREV) {
                        val firstOldItem = feedState.value.feed.firstOrNull()
                        if (firstOldItem != null) {
                            // without scrolling to the to of those new items.
                            autoScrollRef = ConsumableValue(ScrollRef(CommentRef(firstOldItem), smoothScroll = false))
                        }
                    }

                    feedState.value = feedState.value.copy(
                            feed = update.feed,
                            seen = update.feed.filter { item -> seenService.isSeen(item.id) }.mapTo(HashSet()) { it.id },
                            empty = update.remote && update.feed.isEmpty(),
                            autoScrollRef = autoScrollRef,
                            error = null, loading = null)
                }

                is FeedManager.Update.Error -> {
                    val error = update.err

                    logger.error("Error loading the feed", error)

                    if (error is FeedException) {
                        feedState.value = feedState.value.copy(error = null, errorConsumable = ConsumableValue(error))
                    } else {
                        val errorValue: Throwable = when {
                            error is JsonEncodingException ->
                                StringException(error, R.string.could_not_load_feed_json)

                            error.rootCause is ConnectException ->
                                StringException(error, R.string.could_not_load_feed_https)

                            else -> error
                        }

                        feedState.value = feedState.value.copy(error = errorValue, errorConsumable = null)
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
        feedState.update { copy(feed = feed) }
    }

    data class FeedState(
            val feed: Feed = Feed(),
            val seen: Set<Long> = setOf(),
            val errorStr: String? = null,
            val error: Throwable? = null,
            val errorConsumable: ConsumableValue<Throwable>? = null,
            val loading: FeedManager.LoadingSpace? = null,
            val repostRefreshTime: Long = 0,
            val adsVisible: Boolean = false,
            val markItemsAsSeen: Boolean = Settings.get().markItemsAsSeen,
            val preloadedItemIds: LongSparseArray<PreloadManager.PreloadItem> = LongSparseArray(initialCapacity = 0),
            val autoScrollRef: ConsumableValue<ScrollRef>? = null,
            val empty: Boolean = false
    )
}

inline fun <T> MutableStateFlow<T>.update(block: T.() -> T) {
    value = value.block()
}

