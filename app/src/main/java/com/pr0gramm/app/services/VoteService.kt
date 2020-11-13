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
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.checkNotMainThread
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.unsigned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream


/**
 */

class VoteService(private val api: Api,
                  private val seenService: SeenService,
                  private val appDB: AppDB) {

    private val logger = Logger("VoteService")

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
    fun getItemVote(itemId: Long): Flow<Vote> {
        return CachedVote
                .find(appDB.cachedVoteQueries, ITEM, itemId)
                .map { vote -> vote.vote }
                .distinctUntilChanged()
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
            var pendingCollectOp: PendingCollectOp? = null

            logger.info { "Applying $actionCount vote actions" }
            for (idx in 0 until actionCount) {
                val id = actionStream.readIntLe().toLong()
                val action = actionStream.readByte().unsigned

                logger.debug { "Handle action $action with id argument $id" }

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

                when (action) {
                    ACTION_ITEM_COLLECT ->
                        pendingCollectOp = PendingCollectOp(id, true)

                    ACTION_ITEM_UNCOLLECT ->
                        pendingCollectOp = PendingCollectOp(id, false)

                    ACTION_COLLECTION_ID -> {
                        if (pendingCollectOp != null) {
                            executePendingCollectionOp(pendingCollectOp, id)
                            pendingCollectOp = null
                        }
                    }

                    ACTION_COMMENT_FAV ->
                        appDB.favedCommentsQueries.insert(id)

                    ACTION_COMMENT_UNFAV ->
                        appDB.favedCommentsQueries.remove(id)
                }

            }
        }

        logger.info { "Applying vote actions took $watch" }
    }

    private fun executePendingCollectionOp(op: PendingCollectOp, collectionId: Long) {
        if (op.isCollect) {
            appDB.collectionItemQueries.add(itemId = op.itemId, collectionId = collectionId)
        } else {
            appDB.collectionItemQueries.remove(itemId = op.itemId, collectionId = collectionId)
        }
    }

    private class PendingCollectOp(val itemId: Long, val isCollect: Boolean)

    private fun storeFollowAction(userId: Long, state: FollowState) {
        appDB.userFollowEntryQueries.updateUser(userId, state.ordinal)
    }

    /**
     * Tags the given post. This methods adds the tags to the given post
     * and returns a list of tags.
     */
    suspend fun createTags(itemId: Long, tags: List<String>): List<Api.Tag> {
        val tagString = tags.joinToString(",") { tag ->
            tag.replace(',', ' ')
        }

        return withContext(Dispatchers.IO + NonCancellable) {
            val response = api.addTags(null, itemId, tagString)

            appDB.transaction {
                // auto-apply up-vote to newly created tags
                for (tagId in response.tagIds) {
                    storeVoteValue(CachedVote.Type.TAG, tagId, Vote.UP)
                }
            }

            response.tags
        }
    }

    /**
     * Writes a comment to the given post.
     */
    suspend fun postComment(itemId: Long, parentId: Long, comment: String): Api.NewComment {
        return withContext(NonCancellable + Dispatchers.Default) {
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

    fun getCommentVotes(commentIds: List<Long>): Flow<LongSparseArray<Vote>> {
        return findCachedVotes(CachedVote.Type.COMMENT, commentIds).distinctUntilChanged()
    }

    fun getTagVotes(tagIds: List<Long>): Flow<LongSparseArray<Vote>> {
        return findCachedVotes(CachedVote.Type.TAG, tagIds).distinctUntilChanged()
    }

    suspend fun summary(): Map<CachedVote.Type, Summary> = withContext(Dispatchers.Default) {
        val counts = CachedVote.count(appDB.cachedVoteQueries)

        counts.mapValues { entry ->
            Summary(up = entry.value[Vote.UP] ?: 0,
                    down = entry.value[Vote.DOWN] ?: 0)
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

    private class VoteAction(val type: CachedVote.Type, val vote: Vote)

    data class Summary(val up: Int, val down: Int)

    companion object {
        private val VOTE_ACTIONS: Map<Int, VoteAction> = hashMapOf(
                1 to VoteAction(CachedVote.Type.ITEM, Vote.DOWN),
                2 to VoteAction(CachedVote.Type.ITEM, Vote.NEUTRAL),
                3 to VoteAction(CachedVote.Type.ITEM, Vote.UP),
                4 to VoteAction(CachedVote.Type.COMMENT, Vote.DOWN),
                5 to VoteAction(CachedVote.Type.COMMENT, Vote.NEUTRAL),
                6 to VoteAction(CachedVote.Type.COMMENT, Vote.UP),
                7 to VoteAction(CachedVote.Type.TAG, Vote.DOWN),
                8 to VoteAction(CachedVote.Type.TAG, Vote.NEUTRAL),
                9 to VoteAction(CachedVote.Type.TAG, Vote.UP))

        private val FOLLOW_ACTION: Map<Int, FollowState> = hashMapOf(
                12 to FollowState.FOLLOW,
                13 to FollowState.NONE,
                14 to FollowState.SUBSCRIBED,
                15 to FollowState.FOLLOW)

        private const val ACTION_COMMENT_FAV = 16
        private const val ACTION_COMMENT_UNFAV = 17

        private const val ACTION_ITEM_UNCOLLECT = 18
        private const val ACTION_ITEM_COLLECT = 19
        private const val ACTION_COLLECTION_ID = 20

    }
}
