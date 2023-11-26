package com.pr0gramm.app.feed

import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Stats
import com.pr0gramm.app.TimeFactory
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.FeedItemInfoQueries
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.equalsIgnoreCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import java.util.Locale

/**
 * Performs the actual request to get the items for a feed.
 */

interface FeedService {
    suspend fun load(query: FeedQuery): Api.Feed

    suspend fun post(id: Long, bust: Boolean = false): Api.Post

    suspend fun item(itemId: Long): Api.Feed.Item {
        val query = FeedQuery(
            filter = FeedFilter().withFeedType(FeedType.NEW),
            contentTypes = ContentType.AllSet,
            around = itemId,
        )

        return load(query).items.single { item -> item.id == itemId }
    }

    /**
     * Streams feed items - giving one page after the next until
     * the end of the stream.
     */
    fun stream(startQuery: FeedQuery): Flow<Api.Feed>

    data class FeedQuery(
        val filter: FeedFilter, val contentTypes: Set<ContentType>,
        val newer: Long? = null, val older: Long? = null, val around: Long? = null
    )

}

class FeedServiceImpl(
    private val api: Api,
    private val userService: UserService,
    private val itemQueries: FeedItemInfoQueries
) : FeedService {
    private val logger = Logger("FeedService")

    override suspend fun load(query: FeedService.FeedQuery): Api.Feed {
        val feedFilter = query.filter

        // filter by feed-type
        val promoted = if (feedFilter.feedType === FeedType.PROMOTED) 1 else null
        val following = if (feedFilter.feedType === FeedType.STALK) 1 else null

        val flags = ContentType.combine(query.contentTypes)

        val feedType = feedFilter.feedType

        // statistics
        Stats().incrementCounter(
            "feed.loaded",
            "type:" + feedType.name.lowercase(Locale.ROOT)
        )

        val tags = feedFilter.tags?.replaceFirst("^\\s*\\?\\s*".toRegex(), "!")

        return when (feedType) {
            FeedType.RANDOM -> {
                // convert to a query including the x:random flag.
                val bust = Instant.now().millis / 1000L
                val tagsQuery = Tags.joinAnd("!-(x:random | x:$bust)", feedFilter.tags)

                // and load it directly on 'new'
                val feed = load(
                    query.copy(
                        newer = null, older = null, around = null,
                        filter = feedFilter.withFeedType(FeedType.NEW).basicWithTags(tagsQuery)
                    )
                )

                // then shuffle the result
                feed.copy(_items = feed._items?.shuffled())
            }

            FeedType.BESTOF -> {
                // add add s:1000 tag to the query.
                // and add s:700 to nsfw posts.

                val tagsQuery = Tags.joinOr(
                    Tags.joinAnd("s:2000", feedFilter.tags),
                    Tags.joinAnd("s:700 f:nsfw", feedFilter.tags),
                )

                load(query.copy(filter = feedFilter.withFeedType(FeedType.NEW).basicWithTags(tagsQuery)))
            }

            FeedType.CONTROVERSIAL -> {
                // just add the f:controversial flag to the query
                val tagsQuery = Tags.joinAnd("!f:controversial", feedFilter.tags)
                load(query.copy(filter = feedFilter.withFeedType(FeedType.NEW).basicWithTags(tagsQuery)))
            }

            else -> {
                val collection = feedFilter.collection
                val user = feedFilter.username

                val self = userService.loginState
                    .let { loginState ->
                        // we have a user and it is the same as in the query.
                        loginState.name != null && loginState.name.equalsIgnoreCase(user)
                    }
                    .takeIf { self -> self }

                // do the normal query as is.
                val result = api.itemsGet(
                    promoted, following,
                    query.older, query.newer, query.around,
                    flags, tags, collection, self, user
                )

                result.also {
                    AsyncScope.launchIgnoreErrors {
                        logger.time("Cache items from result") {
                            cacheItems(result)
                        }
                    }
                }
            }
        }
    }

    private suspend fun cacheItems(feed: Api.Feed) {
        runInterruptible(Dispatchers.IO) {
            itemQueries.transaction {
                for (item in feed.items) {
                    itemQueries.cache(
                        id = item.id,
                        promotedId = item.promoted,
                        userId = item.userId,
                        image = item.image,
                        thumbnail = item.thumb,
                        fullsize = item.fullsize,
                        user = item.user,
                        up = item.up,
                        down = item.down,
                        mark = item.mark,
                        flags = item.flags,
                        width = item.width,
                        height = item.height,
                        created = item.created.epochSeconds,
                        audio = item.audio,
                        deleted = item.deleted,
                    )

                    for (variant in item.variants) {
                        itemQueries.cacheVariant(
                            itemId = item.id,
                            name = variant.name,
                            path = variant.path,
                        )
                    }

                    for (subtitle in item.subtitles) {
                        itemQueries.cacheSubtitle(
                            itemId = item.id,
                            language = subtitle.language,
                            path = subtitle.path,
                        )
                    }
                }
            }
        }
    }

    override suspend fun post(id: Long, bust: Boolean): Api.Post {
        val buster = if (bust) TimeFactory.currentTimeMillis() else null
        return api.info(id, bust = buster)
    }

    override fun stream(startQuery: FeedService.FeedQuery): Flow<Api.Feed> {
        // move from low to higher numbers if newer is set.
        val upwards = startQuery.newer != null

        return flow {
            var query: FeedService.FeedQuery? = startQuery

            while (true) {
                val currentQuery = query ?: break
                val feed = load(currentQuery)
                emit(feed)

                // get the previous (or next) page from the current set of items.
                query = when {
                    upwards && !feed.isAtStart -> feed.items.maxByOrNull { it.id }
                        ?.let { currentQuery.copy(newer = it.id) }

                    !upwards && !feed.isAtEnd -> feed.items.minByOrNull { it.id }
                        ?.let { currentQuery.copy(older = it.id) }

                    else -> null
                }
            }
        }
    }
}
