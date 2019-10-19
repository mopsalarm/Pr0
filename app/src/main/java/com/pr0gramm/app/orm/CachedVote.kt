package com.pr0gramm.app.orm

import com.pr0gramm.app.Logger
import com.pr0gramm.app.db.CachedVoteQueries
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.Async
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.*
import kotlin.collections.HashMap

/**
 * A cached vote.
 */
data class CachedVote(val itemId: Long, val type: Type, val vote: Vote) {
    enum class Type {
        ITEM, COMMENT, TAG
    }

    companion object {
        private val logger = Logger("CachedVote")
        private val allTypes = Type.values()

        private fun voteId(type: Type, itemId: Long): Long {
            return itemId * 10 + type.ordinal
        }

        private fun toCachedVote(itemId: Long, itemType: Int, voteValue: Int): CachedVote {
            return CachedVote(itemId, type = allTypes[itemType], vote = Vote.ofVoteValue(voteValue))
        }

        fun find(cv: CachedVoteQueries, type: Type, itemId: Long): Flow<CachedVote> {
            return cv.findOne(voteId(type, itemId), this::toCachedVote)
                    .asFlow()
                    .mapToOneOrDefault(CachedVote(itemId, type, Vote.NEUTRAL), Async)
        }

        fun find(cv: CachedVoteQueries, type: Type, ids: List<Long>): Flow<List<CachedVote>> {
            if (ids.isEmpty()) {
                return flowOf(listOf())
            }

            return cv.findSome(ids.map { voteId(type, it) }, this::toCachedVote)
                    .asFlow()
                    .mapToList(Async)
        }

        fun quickSave(cv: CachedVoteQueries, type: Type, itemId: Long, vote: Vote) {
            cv.saveVote(voteId(type, itemId), itemId, type.ordinal, vote.voteValue)
        }

        fun clear(cv: CachedVoteQueries) {
            cv.deleteAll()
        }

        fun count(cv: CachedVoteQueries): Map<Type, Map<Vote, Int>> {
            val result = HashMap<Type, EnumMap<Vote, Int>>()

            logger.time("Counting number of votes") {
                cv.count().executeAsList().forEach { count ->
                    val type = allTypes[count.itemType]
                    val vote = Vote.ofVoteValue(count.voteValue)

                    val votes = result.getOrPut(type, { EnumMap<Vote, Int>(Vote::class.java) })
                    votes[vote] = (votes[vote] ?: 0) + count.count.toInt()
                }
            }

            return result
        }
    }
}
