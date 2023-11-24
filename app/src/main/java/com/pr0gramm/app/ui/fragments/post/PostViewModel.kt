package com.pr0gramm.app.ui.fragments.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.FollowService
import com.pr0gramm.app.services.FollowState
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.services.isMoreRestrictiveContentTypeTag
import com.pr0gramm.app.services.isValidTag
import com.pr0gramm.app.ui.fragments.feed.update
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.containsIgnoreCase
import com.pr0gramm.app.util.rootCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

class PostViewModel(
    item: FeedItem,
    private val requiresCacheBust: Boolean,
    private val userService: UserService,
    private val feedService: FeedService,
    private val voteService: VoteService,
    private val followService: FollowService,
    private val inMemoryCacheService: InMemoryCacheService,
) : ViewModel() {
    private val logger = Logger("PostViewModel")

    private val mutableState = MutableStateFlow(State(item))
    private val commentTreeController = CommentTreeController(item.user)

    val state: StateFlow<State> = mutableState

    var videoIsPaused = false

    private val item: FeedItem
        get() = state.value.item

    init {
        viewModelScope.launch { observeUserInfo() }
        viewModelScope.launch { observeComments() }
        viewModelScope.launch { observeVotesForComments() }
        viewModelScope.launch { observeVotesForTags() }
        viewModelScope.launch { observeItemVote() }
        viewModelScope.launch { observeFollowState() }

        viewModelScope.launch { refreshInternal(initial = true) }
    }

    private suspend fun observeItemVote() {
        voteService.getItemVote(item.id).collect { vote ->
            mutableState.update { previousState ->
                previousState.copy(itemVote = vote)
            }
        }
    }

    private suspend fun observeVotesForTags() {
        mutableState.drop(1)
            // wait for tag ids to change
            .map { state -> state.tags.map { tag -> tag.id } }
            .distinctUntilChanged()

            // fetch em
            .flatMapLatest { tagIds -> voteService.getTagVotes(tagIds) }
            .collect { votes ->
                logger.debug { "Got votes for tags: $votes" }
                mutableState.update { it.copy(tagVotes = votes) }
            }
    }

    private suspend fun observeVotesForComments() {
        commentTreeController.comments
            // wait for change in comment ids
            .map { result -> result.comments.map { it.commentId } }
            .distinctUntilChanged()

            // and fetch votes for those comments
            .flatMapLatest { commentIds -> voteService.getCommentVotes(commentIds) }
            .collect { votes ->
                logger.debug { "Got votes for comments: $votes" }
                commentTreeController.updateVotes(votes)
            }
    }

    private suspend fun observeFollowState() {
        followService.getState(item.userId).collect { followState ->
            mutableState.update { previousState ->
                previousState.copy(followState = followState)
            }
        }
    }


    private suspend fun observeComments() {
        commentTreeController.comments.collect { result ->
            logger.debug { "Got new comment tree of ${result.comments.size} items" }
            mutableState.update { previousState ->
                previousState.copy(
                    comments = result.comments,
                    hasCollapsedComments = result.hasCollapsedComments,
                    commentsLoading = false,
                )
            }
        }
    }

    private suspend fun observeUserInfo() {
        userService.loginStates.collect { loginState ->
            commentTreeController.updateUserInfo(loginState.name, loginState.admin)

            mutableState.update { previousState ->
                previousState.copy(commentsVisible = loginState.authorized)
            }
        }
    }

    private suspend fun refreshInternal(initial: Boolean) {
        if (item.deleted) {
            mutableState.update { previousState ->
                previousState.copy(commentsLoading = false)
            }

            return
        }

        mutableState.update { previousState ->
            previousState.copy(
                refreshing = !initial,

                commentsLoadError = false,

                commentsLoading = initial
                        || previousState.commentsLoadError
                        || previousState.comments.isEmpty(),
            )
        }

        try {
            val (item, post) = coroutineScope {
                val itemAsync = if (initial) null else async {
                    // query the item from the feed again, ignore errors
                    runCatching { FeedItem(feedService.item(item.id)) }.getOrNull()
                }

                // query the post info
                val post = feedService.post(this@PostViewModel.item.id, requiresCacheBust)

                // wait for both results
                Pair(itemAsync?.await() ?: item, post)
            }

            // send comments & votes to the tree helper
            val commentIds = post.comments.map { comment -> comment.id }
            val baseVotes = voteService.getCommentVotes(commentIds).first()
            submitCommentsToTreeController(post.comments, baseVotes)

            val tagIds = post.tags.map { tag -> tag.id }
            val tagVotes = voteService.getTagVotes(tagIds).first()

            mutableState.update { previousState ->
                previousState.copy(
                    item = item,
                    refreshing = false,
                    tags = sortTags(post.tags),
                    tagVotes = tagVotes,
                )
            }
        } catch (err: Exception) {
            if (err.rootCause !is IOException && err !is CancellationException) {
                AndroidUtility.logToCrashlytics(err)
            }

            // remove list of visible comments
            commentTreeController.clearComments()

            mutableState.update { previousState ->
                previousState.copy(
                    refreshing = false,
                    commentsLoading = false,
                    commentsLoadError = true,
                )
            }
        }
    }

    private fun sortTags(tags: List<Api.Tag>): List<Api.Tag> {
        val comparator = compareByDescending<Api.Tag> { it.confidence }.thenBy { it.id }
        return inMemoryCacheService.enhanceTags(item.id, tags).sortedWith(comparator)
    }

    fun refreshAsync() {
        viewModelScope.launch {
            refreshInternal(initial = false)
        }
    }

    fun selectComment(commentId: Long) {
        commentTreeController.selectComment(commentId)
    }

    suspend fun addTagsByUser(tags: List<String>) {
        val previousTags = state.value.tags.map { tag -> tag.text.lowercase(Locale.GERMAN) }

        // allow op to tag a more restrictive content type.
        val op = item.user.equals(userService.name, true) || userService.userIsAdmin

        val newTags = tags
            .filterNot { tag -> previousTags.containsIgnoreCase(tag) }
            .filter { tag -> isValidTag(tag) || (op && isMoreRestrictiveContentTypeTag(previousTags, tag)) }

        if (newTags.isNotEmpty()) {
            logger.info { "Adding new tags $newTags to post" }

            val sortedApiTags = sortTags(voteService.createTags(item.id, newTags))

            mutableState.update { previousState ->
                previousState.copy(tags = sortedApiTags)
            }
        }
    }

    fun updateComments(comments: List<Api.Comment>, newCommentId: Long? = null) {
        viewModelScope.launch {
            val commentIds = comments.map { it.id }

            val baseVotes = voteService.getCommentVotes(commentIds).first().apply {
                put(newCommentId ?: 0, Vote.UP)
            }

            submitCommentsToTreeController(comments, baseVotes)
        }
    }

    private fun submitCommentsToTreeController(comments: List<Api.Comment>, baseVotes: LongSparseArray<Vote>) {
        commentTreeController.updateComments(comments, baseVotes)

        // if we already had no comments before, the tree controller will not emit a new value.
        // because of that, we need to reset the loading state here.
        if (comments.isEmpty()) {
            mutableState.update { previousState ->
                previousState.copy(
                    comments = listOf(),
                    commentsLoading = false,
                    commentsLoadError = false,
                )
            }
        }
    }

    fun expandComment(commentId: Long) {
        commentTreeController.expandComment(commentId)
    }

    fun collapseComment(commentId: Long) {
        commentTreeController.collapseComment(commentId)
    }

    fun collapseComments() {
        val commentIds = state.value.comments.map { comment -> comment.commentId }
        commentTreeController.collapseComments(commentIds)
    }

    data class State(
        val item: FeedItem,
        val refreshing: Boolean = false,
        val itemVote: Vote = Vote.NEUTRAL,
        val tags: List<Api.Tag> = emptyList(),
        val tagVotes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0),
        val comments: List<CommentTree.Item> = emptyList(),
        val hasCollapsedComments: Boolean = false,
        val commentsVisible: Boolean = true,
        val commentsLoading: Boolean = true,
        val commentsLoadError: Boolean = false,
        val followState: FollowState = FollowState.NONE
    )
}