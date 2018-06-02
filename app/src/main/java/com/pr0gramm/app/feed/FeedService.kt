package com.pr0gramm.app.feed

import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.api.categories.ExtraCategories
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.Reducer
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.config.ConfigService
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * Performs the actual request to get the items for a feed.
 */

interface FeedService {
    fun load(query: FeedQuery): Observable<Api.Feed>

    fun post(id: Long): Observable<Api.Post>

    /**
     * Streams feed items - giving one page after the next until
     * the end of the stream.
     */
    fun stream(startQuery: FeedQuery): Observable<Api.Feed>

    data class FeedQuery(val filter: FeedFilter, val contentTypes: Set<ContentType>,
                         val newer: Long? = null, val older: Long? = null, val around: Long? = null)

}

class FeedServiceImpl(private val api: Api,
                      private val extraCategories: ExtraCategories,
                      private val configService: ConfigService) : FeedService {

    private val logger = LoggerFactory.getLogger("FeedService")

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

        // get extended tag query
        val q = SearchQuery(feedFilter.tags)

        val result: Observable<Api.Feed> = when (feedType) {
            FeedType.RANDOM -> extraCategories.api
                    .random(q.tags, flags)
                    .map { feed -> feed.copy(_items = feed.items.shuffled()) }

            FeedType.BESTOF -> {
                val benisScore = Settings.get().bestOfBenisThreshold
                extraCategories.api.bestof(q.tags, user, flags, query.older, benisScore)
            }

            FeedType.CONTROVERSIAL -> extraCategories.api.controversial(q.tags, flags, query.older)

            FeedType.TEXT -> extraCategories.api.text(q.tags, flags, query.older)

            else -> {
                // prepare the call to the official api. The call is only made on subscription.
                val officialCall = api.itemsGet(promoted, following, query.older, query.newer, query.around, flags, q.tags, likes, self, user)

                if (likes == null && configService.config().searchUsingTagService) {
                    extraCategories.api
                            .general(promoted, q.tags, user, flags, query.older, query.newer, query.around)
                            .onErrorResumeNext(officialCall)

                } else if (query.around == null && query.newer == null && q.advanced) {
                    // track the advanced search
                    Track.advancedSearch(q.tags)

                    logger.info("Using general search api, but falling back on old one in case of an error.")
                    extraCategories.api
                                .general(promoted, q.tags, user, flags, query.older, query.newer, query.around)
                                .onErrorResumeNext(officialCall)
                } else {
                    officialCall
                }
            }
        }

        return result
    }

    override fun post(id: Long): Observable<Api.Post> {
        return api.info(id)
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

    private class SearchQuery internal constructor(tags: String?) {
        val advanced: Boolean
        val tags: String?

        init {
            if (tags == null || !tags.trim().startsWith("?")) {
                this.advanced = false
                this.tags = tags
            } else {
                this.advanced = true
                this.tags = tags.replaceFirst("\\s*\\?\\s*".toRegex(), "")
            }
        }
    }
}
