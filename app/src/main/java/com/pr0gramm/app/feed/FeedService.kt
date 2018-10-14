package com.pr0gramm.app.feed

import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.TimeFactory
import com.pr0gramm.app.api.categories.ExtraCategories
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.Reducer
import rx.Observable

/**
 * Performs the actual request to get the items for a feed.
 */

interface FeedService {
    fun load(query: FeedQuery): Observable<Api.Feed>

    fun post(id: Long, bust: Boolean = false): Observable<Api.Post>

    /**
     * Streams feed items - giving one page after the next until
     * the end of the stream.
     */
    fun stream(startQuery: FeedQuery): Observable<Api.Feed>

    data class FeedQuery(val filter: FeedFilter, val contentTypes: Set<ContentType>,
                         val newer: Long? = null, val older: Long? = null, val around: Long? = null)

}

class FeedServiceImpl(private val api: Api,
                      private val extraCategories: ExtraCategories) : FeedService {

    override fun load(query: FeedService.FeedQuery): Observable<Api.Feed> {
        val feedFilter = query.filter

        // filter by feed-type
        val promoted = if (feedFilter.feedType === FeedType.PROMOTED) 1 else null
        val following = if (feedFilter.feedType === FeedType.PREMIUM) 1 else null

        val flags = ContentType.combine(query.contentTypes)
        val user = feedFilter.username

        // FIXME this is quite hacky right now.
        val likes = feedFilter.likes
        val self = if (likes.isNullOrBlank()) null else true

        val feedType = feedFilter.feedType

        // statistics
        Stats.get().incrementCounter("feed.loaded", "type:" + feedType.name.toLowerCase())

        val tags = feedFilter.tags?.replaceFirst("^\\s*\\?\\s*".toRegex(), "!")

        return when (feedType) {
            FeedType.RANDOM -> extraCategories.api
                    .random(tags, flags)
                    .map { feed -> feed.copy(_items = feed.items.shuffled()) }

            FeedType.BESTOF -> {
                val benisScore = Settings.get().bestOfBenisThreshold
                extraCategories.api.bestof(tags, user, flags, query.older, benisScore)
            }

            FeedType.CONTROVERSIAL -> extraCategories.api.controversial(tags, flags, query.older)

            FeedType.TEXT -> extraCategories.api.text(tags, flags, query.older)

            else -> {
                // replace old search with a new one.
                api.itemsGet(promoted, following, query.older, query.newer, query.around, flags, tags, likes, self, user)
            }
        }
    }

    override fun post(id: Long, bust: Boolean): Observable<Api.Post> {
        val buster = if (bust) TimeFactory.currentTimeMillis() else null
        return api.info(id, bust = buster)
    }

    override fun stream(startQuery: FeedService.FeedQuery): Observable<Api.Feed> {
        // move from low to higher numbers if newer is set.
        val upwards = startQuery.newer != null

        return Reducer.iterate(startQuery) { query ->
            return@iterate load(query).toSingle().map { feed ->
                // get the previous (or next) page from the current set of items.
                val nextQuery = if (upwards) {
                    feed.items
                            .takeUnless { feed.isAtStart }
                            ?.maxBy { it.id }
                            ?.let { query.copy(newer = it.id) }
                } else {
                    feed.items
                            .takeUnless { feed.isAtEnd }
                            ?.minBy { it.id }
                            ?.let { query.copy(older = it.id) }
                }

                Reducer.Step(feed, nextQuery)
            }.toBlocking().value()
        }
    }

    private class SearchQuery(val tags: String?)
}
