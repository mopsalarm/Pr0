package com.pr0gramm.app.services

import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


class StatisticsService(private val feedService: FeedService) {
    class Stats(val counts: Map<ContentType, Int>)

    private val uploadsCountCache = ConcurrentHashMap<Long, Stats>()

    fun statsForUploads(username: String): Flow<Stats> {
        return streamStats(uploadsCountCache, FeedFilter()
                .withFeedType(FeedType.NEW)
                .withUser(username))
    }

    private fun streamStats(cache: ConcurrentMap<Long, Stats>, filter: FeedFilter): Flow<Stats> {

        val startAt = cache.keys.max() ?: 0L
        val initialState = cache[startAt] ?: Stats(counts = emptyMap())

        return feedService.stream(FeedService.FeedQuery(filter, ContentType.AllSet, newer = startAt)).scan(initialState) { state, feed ->
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
