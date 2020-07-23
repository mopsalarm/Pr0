package com.pr0gramm.app.feed

import com.pr0gramm.app.Instant
import com.pr0gramm.app.Stats
import com.pr0gramm.app.TimeFactory
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.util.equalsIgnoreCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

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

    data class FeedQuery(val filter: FeedFilter, val contentTypes: Set<ContentType>,
                         val newer: Long? = null, val older: Long? = null, val around: Long? = null)

}

class FeedServiceImpl(private val api: Api, private val userService: UserService) : FeedService {
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
                "type:" + feedType.name.toLowerCase(Locale.ROOT))

        val tags = feedFilter.tags?.replaceFirst("^\\s*\\?\\s*".toRegex(), "!")

        return when (feedType) {
            FeedType.RANDOM -> {
                // convert to a query including the x:random flag.
                val bust = Instant.now().millis / 1000L
                val tagsQuery = Tags.join("!-(x:random | x:$bust)", feedFilter.tags)

                // and load it directly on 'new'
                val feed = load(query.copy(
                        newer = null, older = null, around = null,
                        filter = feedFilter.withFeedType(FeedType.NEW).withTags(tagsQuery)))

                // then shuffle the result
                feed.copy(_items = feed._items?.shuffled())
            }

            FeedType.BESTOF -> {
                // add a s:2000 tag to the query.
                val tagsQuery = Tags.join("!s:2000", feedFilter.tags)
                load(query.copy(filter = feedFilter.withFeedType(FeedType.NEW).withTags(tagsQuery)))
            }

            FeedType.CONTROVERSIAL -> {
                // just add the f:controversial flag to the query
                val tagsQuery = Tags.join("!f:controversial", feedFilter.tags)
                load(query.copy(filter = feedFilter.withFeedType(FeedType.NEW).withTags(tagsQuery)))
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
                api.itemsGet(promoted, following,
                        query.older, query.newer, query.around,
                        flags, tags, collection, self, user)
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
                    upwards && !feed.isAtStart -> feed.items.maxBy { it.id }?.let { currentQuery.copy(newer = it.id) }
                    !upwards && !feed.isAtEnd -> feed.items.minBy { it.id }?.let { currentQuery.copy(older = it.id) }
                    else -> null
                }
            }
        }
    }
}
