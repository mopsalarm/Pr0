package com.pr0gramm.app.services

import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedType
import rx.Observable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit


class StatisticsService(private val feedService: FeedService) {
    class Stats(val counts: Map<ContentType, Int>)

    private val uploadsCountCache = ConcurrentHashMap<Long, Stats>()
    private val favoritesCountCache = ConcurrentHashMap<Long, Stats>()

    fun statsForUploads(username: String): Observable<Stats> {
        return streamStats(uploadsCountCache, FeedFilter()
                .withFeedType(FeedType.NEW)
                .withUser(username))
    }

    // TODO remove?
//    fun statsForFavorites(username: String): Observable<Stats> {
//        return streamStats(favoritesCountCache, FeedFilter()
//                .withFeedType(FeedType.NEW)
//                .withLikes(username))
//    }

    private fun streamStats(cache: ConcurrentMap<Long, Stats>, filter: FeedFilter): Observable<Stats> {

        val startAt = cache.keys.max() ?: 0L
        val initialState = cache[startAt] ?: Stats(counts = emptyMap())

        return feedService.stream(FeedService.FeedQuery(filter, ContentType.AllSet, newer = startAt))
                .timeout(1, TimeUnit.MINUTES)

                .scan(initialState) { state, feed ->
                    val counts = state.counts.toMutableMap()

                    // count each flag of each item once.
                    for (type in feed.items.flatMap { ContentType.decompose(it.flags) }) {
                        counts[type] = (counts[type] ?: 0) + 1
                    }

                    val newState = Stats(counts.toMap())
                    if (!feed.isAtEnd && !feed.isAtStart) {
                        // this is a complete page, just cache the state at
                        // it's most recent item
                        feed.items.maxBy { it.id }?.let { item ->
                            cache[item.id] = newState
                        }
                    }

                    newState
                }
    }
}
