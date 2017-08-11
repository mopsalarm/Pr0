package com.pr0gramm.app.feed

import com.google.common.base.Strings
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
     * Loads all values from the given feed query into memory and returns
     * one list with items. Be careful!
     */
    fun loadAll(startQuery: FeedQuery): Observable<List<Api.Feed.Item>>

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
        val self = if (Strings.isNullOrEmpty(likes)) null else true

        val feedType = feedFilter.feedType

        // statistics
        Stats.get().incrementCounter("feed.loaded", "type:" + feedType.name.toLowerCase())

        // get extended tag query
        val q = SearchQuery(feedFilter.tags)

        when (feedType) {
            FeedType.RANDOM -> return extraCategories.api.random(q.tags, flags)

            FeedType.BESTOF -> {
                val benisScore = Settings.get().bestOfBenisThreshold
                return extraCategories.api.bestof(q.tags, user, flags, query.older, benisScore)
            }

            FeedType.CONTROVERSIAL -> return extraCategories.api.controversial(q.tags, flags, query.older)

            FeedType.TEXT -> return extraCategories.api.text(q.tags, flags, query.older)

            else -> {
                // prepare the call to the official api. The call is only made on subscription.
                val officialCall = api.itemsGet(promoted, following, query.older, query.newer, query.around, flags, q.tags, likes, self, user)

                if (likes == null && configService.config().searchUsingTagService) {
                    return extraCategories.api
                            .general(promoted, q.tags, user, flags, query.older, query.newer, query.around)
                            .onErrorResumeNext(officialCall)

                } else if (query.around == null && query.newer == null) {
                    if (q.advanced) {
                        // track the advanced search
                        Track.advancedSearch(q.tags)

                        logger.info("Using general search api, but falling back on old one in case of an error.")
                        return extraCategories.api
                                .general(promoted, q.tags, user, flags, query.older, query.newer, query.around)
                                .onErrorResumeNext(officialCall)
                    }
                }

                return officialCall
            }
        }
    }

    override fun post(id: Long): Observable<Api.Post> {
        return api.info(id)
    }

    override fun loadAll(startQuery: FeedService.FeedQuery): Observable<List<Api.Feed.Item>> {
        return Reducer.unpack(Reducer.reduceToList(startQuery) { query ->
            return@reduceToList load(query).toSingle().map { feed ->
                val nextQuery = feed.items
                        ?.takeUnless { feed.isAtEnd }
                        ?.lastOrNull()
                        ?.let { query.copy(older = it.id) }

                Reducer.Step(feed.items, nextQuery)
            }
        })
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
