package com.pr0gramm.app.feed

import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.TimeFactory
import com.pr0gramm.app.api.categories.ExtraCategories
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.createObservable
import kotlinx.coroutines.runBlocking
import rx.Emitter
import rx.Observable

/**
 * Performs the actual request to get the items for a feed.
 */

interface FeedService {
    suspend fun load(query: FeedQuery): Api.Feed

    suspend fun post(id: Long, bust: Boolean = false): Api.Post

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

    override suspend fun load(query: FeedService.FeedQuery): Api.Feed {
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
            FeedType.RANDOM -> {
                val feed = extraCategories.api.random(tags, flags).await()
                feed.copy(_items = feed.items.shuffled())
            }

            FeedType.BESTOF -> {
                val benisScore = Settings.get().bestOfBenisThreshold
                extraCategories.api.bestof(tags, user, flags, query.older, benisScore).await()
            }

            FeedType.CONTROVERSIAL -> extraCategories.api.controversial(tags, flags, query.older).await()

            FeedType.TEXT -> extraCategories.api.text(tags, flags, query.older).await()

            else -> {
                // replace old search with a new one.
                api.itemsGet(promoted, following, query.older, query.newer, query.around, flags, tags, likes, self, user).await()
            }
        }
    }

    override suspend fun post(id: Long, bust: Boolean): Api.Post {
        val buster = if (bust) TimeFactory.currentTimeMillis() else null
        return api.info(id, bust = buster).await()
    }

    override fun stream(startQuery: FeedService.FeedQuery): Observable<Api.Feed> {
        // move from low to higher numbers if newer is set.
        val upwards = startQuery.newer != null

        return createObservable(Emitter.BackpressureMode.BUFFER) { emitter ->
            runBlocking {
                try {
                    var query: FeedService.FeedQuery? = startQuery

                    while (true) {
                        val currentQuery = query ?: break
                        val feed = load(currentQuery)
                        emitter.onNext(feed)

                        // get the previous (or next) page from the current set of items.
                        query = if (upwards) {
                            feed.items.takeUnless { feed.isAtStart }?.maxBy { it.id }
                                    ?.let { currentQuery.copy(newer = it.id) }
                        } else {
                            feed.items.takeUnless { feed.isAtEnd }?.minBy { it.id }
                                    ?.let { currentQuery.copy(older = it.id) }
                        }
                    }

                    emitter.onCompleted()


                } catch (err: Throwable) {
                    emitter.onError(err)
                }
            }
        }
    }
}
