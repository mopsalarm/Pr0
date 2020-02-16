package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.Stopwatch
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.AppDB
import com.pr0gramm.app.decodeBase64
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.orm.CachedVote.Type.ITEM
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.util.Databases.withTransaction
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.checkNotMainThread
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.unsigned
import com.squareup.sqlbrite.BriteDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream


/**
 */

class VoteService(private val api: Api,
                  private val seenService: SeenService,
                  private val database: BriteDatabase,
                  private val appDB: AppDB) {

    /**
     * Votes a post. This sends a request to the server, so you need to be signed in
     * to vote posts.

     * @param item The item that is to be voted
     * @param vote The vote to send to the server
     */
    suspend fun vote(item: FeedItem, vote: Vote) {
        logger.info { "Voting feed item ${item.id} $vote" }
        Track.votePost(vote)

        doInBackground { storeVoteValueInTx(CachedVote.Type.ITEM, item.id, vote) }
        api.vote(null, item.id, vote.voteValue)
    }

    suspend fun vote(comment: Api.Comment, vote: Vote) {
        logger.info { "Voting comment ${comment.id} $vote" }
        Track.voteComment(vote)

        doInBackground { storeVoteValueInTx(CachedVote.Type.COMMENT, comment.id, vote) }
        api.voteComment(null, comment.id, vote.voteValue)
    }

    suspend fun vote(tag: Api.Tag, vote: Vote) {
        logger.info { "Voting tag ${tag.id} $vote" }
        Track.voteTag(vote)

        doInBackground { storeVoteValueInTx(CachedVote.Type.TAG, tag.id, vote) }
        api.voteTag(null, tag.id, vote.voteValue)
    }

    /**
     * Observes the votes for an item.
     * @param item The item to get the vote for.
     */
    fun getVote(item: FeedItem): Flow<Vote> {
        return CachedVote.find(appDB.cachedVoteQueries, ITEM, item.id).map { vote -> vote.vote }
    }

    /**
     * Stores the vote value. This creates a transaction to prevent lost updates.

     * @param type   The type of vote to store in the vote cache.
     * @param itemId The id of the item to vote
     * @param vote   The vote to store for that item
     */
    private fun storeVoteValueInTx(type: CachedVote.Type, itemId: Long, vote: Vote) {
        checkNotMainThread()
        appDB.cachedVoteQueries.transaction {
            storeVoteValue(type, itemId, vote)
        }
    }

    /**
     * Stores a vote value for an item with the given id.
     * This method must be called inside of an transaction to guarantee
     * consistency.

     * @param type   The type of vote to store in the vote cache.
     * *
     * @param itemId The id of the item to vote
     * *
     * @param vote   The vote to store for that item
     */
    private fun storeVoteValue(type: CachedVote.Type, itemId: Long, vote: Vote) {
        checkNotMainThread()
        CachedVote.quickSave(appDB.cachedVoteQueries, type, itemId, vote)
    }

    /**
     * Applies the given voting actions from the log.

     * @param actions The actions from the log to apply.
     */
    fun applyVoteActions(actions: String) {
        if (actions.isEmpty())
            return

        val decoded = actions.decodeBase64()
        require(decoded.size % 5 == 0) {
            "Length of vote log must be a multiple of 5"
        }


        val actionCount = decoded.size / 5
        val actionStream = ByteArrayInputStream(decoded).source().buffer()

        val watch = Stopwatch()
        appDB.transaction {
            logger.info { "Applying $actionCount vote actions" }
            for (idx in 0 until actionCount) {
                val id = actionStream.readIntLe().toLong()
                val action = actionStream.readByte().unsigned

                val voteAction = VOTE_ACTIONS[action]
                if (voteAction != null) {
                    storeVoteValue(voteAction.type, id, voteAction.vote)
                    if (voteAction.type == ITEM) {
                        seenService.markAsSeen(id)
                    }
                }

                val followAction = FOLLOW_ACTION[action]
                if (followAction != null) {
                    storeFollowAction(id, followAction)
                }
            }
        }

        logger.info { "Applying vote actions took $watch" }
    }

    private fun storeFollowAction(userId: Long, state: FollowState) {
        appDB.userFollowEntryQueries.updateUser(userId, state.ordinal)
    }

    /**
     * Tags the given post. This methods adds the tags to the given post
     * and returns a list of tags.
     */
    suspend fun tag(itemId: Long, tags: List<String>): List<Api.Tag> {
        val tagString = tags.map { tag -> tag.replace(',', ' ') }.joinToString(",")

        val response = api.addTags(null, itemId, tagString)

        withTransaction(database) {
            // auto-apply up-vote to newly created tags
            for (tagId in response.tagIds) {
                storeVoteValue(CachedVote.Type.TAG, tagId, Vote.UP)
            }
        }

        return response.tags
    }

    /**
     * Writes a comment to the given post.
     */
    suspend fun postComment(itemId: Long, parentId: Long, comment: String): Api.NewComment {
        return withBackgroundContext(NonCancellable) {
            val response = api.postComment(null, itemId, parentId, comment)

            val commentId = response.commentId
            if (commentId != null) {
                // store the implicit upvote for the comment.
                storeVoteValueInTx(CachedVote.Type.COMMENT, commentId, Vote.UP)
            }

            response
        }
    }

    /**
     * Removes all votes from the vote cache.
     */
    fun clear() {
        logger.info { "Removing all items from vote cache" }
        CachedVote.clear(appDB.cachedVoteQueries)
    }

    /**
     * Gets the votes for the given comments

     * @param comments A list of comments to get the votes for.
     * *
     * @return A map containing the vote from commentId to vote
     */
    fun getCommentVotes(comments: List<Api.Comment>): Flow<LongSparseArray<Vote>> {
        val ids = comments.map { it.id }
        return findCachedVotes(CachedVote.Type.COMMENT, ids)
    }

    fun getTagVotes(tags: List<Api.Tag>): Flow<LongSparseArray<Vote>> {
        val ids = tags.map { it.id }
        return findCachedVotes(CachedVote.Type.TAG, ids)
    }

    suspend fun summary(): Map<CachedVote.Type, Summary> = withBackgroundContext {
        val counts = CachedVote.count(appDB.cachedVoteQueries)

        counts.mapValues { entry ->
            Summary(up = entry.value[Vote.UP] ?: 0,
                    down = entry.value[Vote.DOWN] ?: 0,
                    fav = entry.value[Vote.FAVORITE] ?: 0)
        }
    }

    private fun findCachedVotes(type: CachedVote.Type, ids: List<Long>): Flow<LongSparseArray<Vote>> {
        return CachedVote.find(appDB.cachedVoteQueries, type, ids).map { cachedVotes ->
            val result = LongSparseArray<Vote>(cachedVotes.size)

            // add "NEUTRAL" votes for every unknown item
            ids.forEach { result.put(it, Vote.NEUTRAL) }

            for (cachedVote in cachedVotes) {
                result.put(cachedVote.itemId, cachedVote.vote)
            }

            result
        }
    }

    private class VoteAction(internal val type: CachedVote.Type, internal val vote: Vote)

    data class Summary(val up: Int, val down: Int, val fav: Int)

    companion object {
        private val logger = Logger("VoteService")

        private val VOTE_ACTIONS = mapOf(
                1 to VoteAction(CachedVote.Type.ITEM, Vote.DOWN),
                2 to VoteAction(CachedVote.Type.ITEM, Vote.NEUTRAL),
                3 to VoteAction(CachedVote.Type.ITEM, Vote.UP),
                4 to VoteAction(CachedVote.Type.COMMENT, Vote.DOWN),
                5 to VoteAction(CachedVote.Type.COMMENT, Vote.NEUTRAL),
                6 to VoteAction(CachedVote.Type.COMMENT, Vote.UP),
                7 to VoteAction(CachedVote.Type.TAG, Vote.DOWN),
                8 to VoteAction(CachedVote.Type.TAG, Vote.NEUTRAL),
                9 to VoteAction(CachedVote.Type.TAG, Vote.UP),
                10 to VoteAction(CachedVote.Type.ITEM, Vote.FAVORITE),
                11 to VoteAction(CachedVote.Type.COMMENT, Vote.FAVORITE))

        private val FOLLOW_ACTION = mapOf(
                12 to FollowState.FOLLOW,
                13 to FollowState.NONE,
                14 to FollowState.SUBSCRIBED,
                15 to FollowState.FOLLOW)
    }
}
