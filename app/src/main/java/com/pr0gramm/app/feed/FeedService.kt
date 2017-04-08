package com.pr0gramm.app.feed

import com.google.common.base.Strings
import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.api.categories.ExtraCategoryApiProvider
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.config.ConfigService
import org.slf4j.LoggerFactory
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs the actual request to get the items for a feed.
 */
@Singleton
class FeedService @Inject
constructor(private val mainApi: Api,
            categoryApi: ExtraCategoryApiProvider,
            private val settings: Settings,
            private val configService: ConfigService) {

    private val categoryApi = categoryApi.get()

    fun getFeedItems(query: FeedQuery): Observable<Api.Feed> {
        val feedFilter = query.filter

        // filter by feed-type
        val promoted = if (feedFilter.feedType === FeedType.PROMOTED) 1 else null
        val following = if (feedFilter.feedType === FeedType.PREMIUM) 1 else null

        val flags = ContentType.combine(query.contentTypes)
        val tags = feedFilter.tags.orNull()
        val user = feedFilter.username.orNull()

        // FIXME this is quite hacky right now.
        val likes = feedFilter.likes.orNull()
        val self = if (Strings.isNullOrEmpty(likes)) null else true

        val feedType = feedFilter.feedType

        // statistics
        Stats.get().incrementCounter("feed.loaded", "type:" + feedType.name.toLowerCase())

        // get extended tag query
        val q = SearchQuery(tags)

        when (feedType) {
            FeedType.RANDOM -> return categoryApi.random(q.tags, flags)

            FeedType.BESTOF -> {
                val benisScore = settings.bestOfBenisThreshold()
                return categoryApi.bestof(q.tags, user!!, flags, query.older, benisScore)
            }

            FeedType.CONTROVERSIAL -> return categoryApi.controversial(q.tags, flags, query.older)

            FeedType.TEXT -> return categoryApi.text(q.tags, flags, query.older)

            else -> {
                // prepare the call to the official api. The call is only made on subscription.
                val officialCall = mainApi.itemsGet(promoted, following, query.older, query.newer, query.around, flags, q.tags, likes, self, user)

                if (likes == null && configService.config().searchUsingTagService()) {
                    return categoryApi
                            .general(promoted, q.tags, user!!, flags, query.older, query.newer, query.around)
                            .onErrorResumeNext(officialCall)

                } else if (query.around == null && query.newer == null) {
                    if (q.advanced) {
                        // track the advanced search
                        Track.advancedSearch(q.tags)

                        logger.info("Using general search api, but falling back on old one in case of an error.")
                        return categoryApi
                                .general(promoted, q.tags, user!!, flags, query.older, query.newer, query.around)
                                .onErrorResumeNext(officialCall)
                    }
                }

                return officialCall
            }
        }
    }

    fun loadPostDetails(id: Long): Observable<Api.Post> {
        return mainApi.info(id)
    }

    data class FeedQuery(val filter: FeedFilter, val contentTypes: Set<ContentType>,
                         val newer: Long? = null, val older: Long? = null, val around: Long? = null)

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

    companion object {
        private val logger = LoggerFactory.getLogger("FeedService")
    }
}
