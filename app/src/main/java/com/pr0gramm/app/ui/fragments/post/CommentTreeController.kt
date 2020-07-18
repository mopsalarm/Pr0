package com.pr0gramm.app.ui.fragments.post

import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.fragments.feed.update
import com.pr0gramm.app.util.LongSparseArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * Handles updates to comments ands performs tree calculation in the background.
 */
class CommentTreeController(op: String) {
    private val logger = Logger("CommentTreeHelper")

    private val inputState = MutableStateFlow(CommentTree.Input(isValid = false, op = op))

    val comments = inputState
            .dropWhile { state -> !state.isValid }
            .mapLatest { state -> calculateVisibleComments(state) }

    // update the currently selected comment
    fun selectComment(id: Long) {
        inputState.update { it.copy(selectedCommentId = id) }
    }

    fun updateUserInfo(selfUser: String?, isAdmin: Boolean) {
        inputState.update { it.copy(self = selfUser, isAdmin = isAdmin) }
    }

    fun collapseComment(commentId: Long) {
        inputState.update { it.copy(collapsed = it.collapsed + commentId) }
    }

    fun expandComment(commentId: Long) {
        inputState.update { it.copy(collapsed = it.collapsed - commentId) }
    }

    fun updateVotes(currentVotes: LongSparseArray<Vote>) {
        inputState.update { previousState ->
            previousState.copy(
                    baseVotes = calculateBaseVotes(currentVotes),
                    currentVotes = currentVotes.clone(),
            )
        }
    }

    fun updateComments(comments: List<Api.Comment>, currentVotes: LongSparseArray<Vote>) {
        inputState.update { previousState ->
            previousState.copy(
                    isValid = true,
                    allComments = comments.toList(),
                    baseVotes = calculateBaseVotes(currentVotes),
                    currentVotes = currentVotes.clone(),
            )
        }
    }

    fun clearComments() {
        inputState.update { previousState ->
            previousState.copy(allComments = listOf())
        }
    }

    private fun calculateBaseVotes(currentVotes: LongSparseArray<Vote>): LongSparseArray<Vote> {
        // start with the current votes and reset the existing base votes
        return currentVotes.clone().apply {
            putAll(inputState.value.baseVotes)
        }
    }

    private suspend fun calculateVisibleComments(inputState: CommentTree.Input): List<CommentTree.Item> {
        logger.debug {
            "Will run an update for current state ${System.identityHashCode(inputState)} " +
                    "(${inputState.allComments.size} comments, " +
                    "selected=${inputState.selectedCommentId})"
        }

        if (inputState.allComments.isEmpty()) {
            // quickly return if there are no comments to display
            return listOf()
        }

        return withContext(Dispatchers.Default) {
            logger.debug { "Running update in thread ${Thread.currentThread().name}" }
            CommentTree(inputState).visibleComments
        }
    }
}