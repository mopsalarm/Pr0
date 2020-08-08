package com.pr0gramm.app.ui.fragments.post

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.views.CommentScore
import com.pr0gramm.app.util.LongSparseArray
import kotlin.math.absoluteValue
import kotlin.math.sign

class CommentTree(val state: Input) {
    private val byId = state.allComments.associateByTo(hashMapOf()) { it.id }
    private val byParent = state.allComments.groupByTo(hashMapOf()) { it.parentId }

    private val depthCache = hashMapOf<Long, Int>()

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     */
    private val linearizedComments: List<Api.Comment> = run {
        // bring op comments to top, then order by confidence.
        val ordering = compareBy<Api.Comment> { it.name == state.op }.thenBy { it.confidence }
        byParent.values.forEach { children -> children.sortWith(ordering) }


        mutableListOf<Api.Comment>().apply {
            val stack = byParent[0]?.toMutableList() ?: return@apply

            while (stack.isNotEmpty()) {
                // get next element
                val comment = stack.removeAt(stack.lastIndex)

                if (comment.id !in state.collapsed) {
                    // and add all children to stack if the element itself is not collapsed
                    byParent[comment.id]?.let { stack.addAll(it) }
                }

                // also add element to result
                add(comment)
            }
        }
    }

    val visibleComments: List<Item> = run {
        val depths = IntArray(linearizedComments.size)
        val spacings = IntArray(linearizedComments.size)

        for ((idx, comment) in linearizedComments.withIndex()) {
            val depth = depthOf(comment)

            // cache depths by index so we can easily scan back later
            depths[idx] = depth

            // set bit for the current comment
            var spacing = Spacings(spacings[idx]).withLineAt(depth)

            if (idx > 0 && depths[idx - 1] < depth) {
                // mark as first child
                spacing = spacing.withIsFirstChild()
            }

            // and cache the spacing value for later
            spacings[idx] = spacing.value

            // scan back until we find a comment with the same depth,
            // or the parent comment
            for (offset in (1..idx)) {
                if (depths[idx - offset] <= depth) {
                    break
                }

                spacings[idx - offset] = Spacings(spacings[idx - offset]).withLineAt(depth).value
            }

        }

        linearizedComments.mapIndexed { idx, comment ->
            val vote = currentVote(comment)
            val isCollapsed = comment.id in state.collapsed && comment.id in byParent

            return@mapIndexed Item(
                    comment = comment,
                    vote = vote,
                    depth = depths[idx],
                    spacings = Spacings(spacings[idx]),
                    hasChildren = comment.id in byParent,
                    currentScore = commentScore(comment, vote),
                    hasOpBadge = state.op == comment.name,
                    hiddenCount = if (isCollapsed) countSubTreeComments(comment) else null,
                    pointsVisible = state.isAdmin || state.self == comment.name,
                    selectedComment = state.selectedCommentId == comment.id)
        }
    }

    private fun currentVote(comment: Api.Comment): Vote {
        return state.currentVotes[comment.id] ?: Vote.NEUTRAL
    }

    private fun baseVote(comment: Api.Comment): Vote {
        return state.baseVotes[comment.id] ?: Vote.NEUTRAL
    }

    private fun commentScore(comment: Api.Comment, currentVote: Vote): CommentScore {
        val delta = (currentVote.voteValue - baseVote(comment).voteValue).sign

        return CommentScore(
                comment.up + delta.coerceAtLeast(0),
                comment.down + delta.coerceAtMost(0).absoluteValue)
    }

    private fun countSubTreeComments(start: Api.Comment): Int {
        var count = 0

        val queue = mutableListOf(start)
        while (queue.isNotEmpty()) {
            val comment = queue.removeAt(queue.lastIndex)
            byParent[comment.id]?.let { children ->
                queue.addAll(children)
                count += children.size
            }
        }

        return count
    }

    private fun depthOf(comment: Api.Comment): Int {
        return depthCache.getOrPut(comment.id) {
            var current = comment
            var depth = 0

            while (true) {
                depth++

                // check if parent is already cached, then we'll take the cached value
                depthCache[current.parentId]?.let { depthOfParent ->
                    return@getOrPut depth + depthOfParent
                }

                // it is not, lets move up the tree
                current = byId[current.parentId] ?: break
            }

            return@getOrPut depth
        }
    }

    data class Input(
            val isValid: Boolean,
            val allComments: List<Api.Comment> = listOf(),
            val currentVotes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0),
            val baseVotes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0),
            val collapsed: Set<Long> = setOf(),
            val op: String? = null,
            val self: String? = null,
            val isAdmin: Boolean = false,
            val selectedCommentId: Long = 0L)

    data class Item(
            val comment: Api.Comment,
            val vote: Vote,
            val depth: Int,
            val spacings: Spacings,
            val hasChildren: Boolean,
            val currentScore: CommentScore,
            val hasOpBadge: Boolean,
            val hiddenCount: Int?,
            val pointsVisible: Boolean,
            val selectedComment: Boolean,
    ) {
        val commentId: Long get() = comment.id
        val isCollapsed: Boolean get() = hiddenCount != null
        val canCollapse: Boolean get() = hasChildren && hiddenCount == null
    }
}

data class Spacings(val value: Int) {
    val isFirstChild: Boolean
        get() = value and 0x1 != 0

    val maxLineIsAt: Int
        get() = (20 downTo 0).firstOrNull { hasLineAt(it) } ?: -1

    fun lines(from: Int): List<Int> {
        return (from until 20).filter { hasLineAt(it) }
    }

    fun withLineAt(depth: Int): Spacings {
        return Spacings(value or (1 shl (1 + depth)))
    }

    fun withIsFirstChild(): Spacings {
        return Spacings(value or 0x1)
    }

    private fun hasLineAt(depth: Int): Boolean {
        return value and (1 shl (1 + depth)) != 0
    }
}