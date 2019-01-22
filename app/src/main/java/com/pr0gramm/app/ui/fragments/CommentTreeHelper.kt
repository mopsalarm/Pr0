package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.ui.views.CommentScore
import com.pr0gramm.app.ui.views.CommentSpacerView
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.ui.views.VoteView
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import rx.Observable
import rx.subjects.BehaviorSubject
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

/**
 */
abstract class CommentTreeHelper : CommentView.Listener {
    private val logger = Logger("CommentTreeHelper")

    private var state: CommentTree.Input by StateProperty()
    private var stateUpdateSync = false

    private val subject = BehaviorSubject.create<List<CommentTree.Item>>(listOf())
    val itemsObservable = subject as Observable<List<CommentTree.Item>>

    // update the currently selected comment
    fun selectComment(id: Long) {
        if (state.selectedCommentId != id) {
            state = state.copy(selectedCommentId = id)
        }
    }

    fun userIsAdmin(admin: Boolean) {
        if (state.isAdmin != admin) {
            state = state.copy(isAdmin = admin)
        }
    }

    override fun collapseComment(comment: Api.Comment) {
        state = state.copy(collapsed = state.collapsed + comment.id)
    }

    override fun expandComment(comment: Api.Comment) {
        state = state.copy(collapsed = state.collapsed - comment.id)
    }

    fun updateVotes(votes: TLongObjectMap<Vote>) {
        val currentVotes = TLongObjectHashMap(votes)

        // add new votes as base votes
        val baseVotes = TLongObjectHashMap(state.baseVotes)

        votes.forEachEntry { id, vote ->
            baseVotes.putIfAbsent(id, vote)
            true
        }

        state = state.copy(baseVotes = baseVotes, currentVotes = currentVotes)
    }

    fun updateComments(comments: List<Api.Comment>, synchronous: Boolean = false,
                       extraChanges: (CommentTree.Input) -> CommentTree.Input) {


        stateUpdateSync = synchronous
        state = extraChanges(state.copy(allComments = comments.toList()))
    }

    private fun updateVisibleComments() {
        checkMainThread()

        val targetState = state

        logger.debug {
            "Will run an update for current state ${System.identityHashCode(targetState)} " +
                    "(${targetState.allComments.size} comments, " +
                    "selected=${targetState.selectedCommentId})"
        }

        val runThisUpdateAsync = !stateUpdateSync && !targetState.allComments.isEmpty()
        stateUpdateSync = false

        Observable
                .fromCallable { CommentTree(targetState).visibleComments }
                .ignoreError()
                .withIf(runThisUpdateAsync) {
                    logger.info { "Running update in background" }
                    subscribeOnBackground().observeOnMainThread()
                }
                .subscribe { visibleComments ->
                    logger.debug {
                        "About to set state ${System.identityHashCode(targetState)} " +
                                "(expected=${System.identityHashCode(state)}, ok=${targetState === state})"
                    }

                    // verify that we still got the right state
                    if (state === targetState) {
                        subject.onNext(visibleComments)
                    } else {
                        logger.debug { "List of comments already stale." }
                    }
                }
    }

    private inner class StateProperty : ObservableProperty<CommentTree.Input>(CommentTree.Input()) {
        override fun beforeChange(property: KProperty<*>, oldValue: CommentTree.Input, newValue: CommentTree.Input): Boolean {
            // only accept change if something really did change.
            return oldValue != newValue
        }

        override fun afterChange(property: KProperty<*>, oldValue: CommentTree.Input, newValue: CommentTree.Input) {
            updateVisibleComments()
        }
    }
}

class CommentView(val parent: ViewGroup,
                  private val actionListener: Listener) : RecyclerView.ViewHolder(inflateCommentViewLayout(parent)) {

    private val maxLevels = ConfigService.get(itemView.context).commentsMaxLevels

    private val id = LongValueHolder(0L)

    private val vote: VoteView = itemView.find(R.id.voting)
    private val reply: ImageView = itemView.find(R.id.action_reply)
    private val content: TextView = itemView.find(R.id.comment)
    private val senderInfo: SenderInfoView = itemView.find(R.id.sender_info)

    private val more: View = itemView.find(R.id.action_more)
    private val fav: ImageView = itemView.find(R.id.action_kfav)
    private val expand: TextView = itemView.find(R.id.action_expand)

    private val commentView = itemView as CommentSpacerView

    @SuppressLint("SetTextI18n")
    fun set(item: CommentTree.Item) {
        val comment = item.comment
        val context = itemView.context

        // skip animations if this  comment changed
        val changed = id.update(comment.id)

        commentView.depth = item.depth
        reply.visible = item.depth < maxLevels

        senderInfo.setSenderName(comment.name, comment.mark)
        senderInfo.setOnSenderClickedListener {
            actionListener.onCommentAuthorClicked(comment)
        }

        // show the points
        val scoreVisibleThreshold = Instant.now() - Duration.hours(1)
        if (item.pointsVisible || comment.created.isBefore(scoreVisibleThreshold)) {
            senderInfo.setPoints(item.currentScore)
        } else {
            senderInfo.setPointsUnknown()
        }

        // and the date of the post
        senderInfo.setDate(comment.created)

        // enable or disable the badge
        senderInfo.setBadgeOpVisible(item.hasOpBadge)

        // and register a vote handler
        vote.setVoteState(item.vote, animate = !changed)
        vote.onVote = { vote -> actionListener.onCommentVoteClicked(item.comment, vote) }

        // set alpha for the sub views. sadly, setting alpha on view.itemView is not working
        content.alpha = if (item.vote === Vote.DOWN) 0.5f else 1f
        senderInfo.alpha = if (item.vote === Vote.DOWN) 0.5f else 1f

        reply.setOnClickListener {
            actionListener.onReplyClicked(comment)
        }

        if (item.selectedComment) {
            val color = ContextCompat.getColor(context, R.color.selected_comment_background)
            itemView.setBackgroundColor(color)
        } else {
            ViewCompat.setBackground(itemView, null)
        }

        fav.let { fav ->
            val isFavorite = item.vote == Vote.FAVORITE
            val newVote = if (isFavorite) Vote.UP else Vote.FAVORITE

            if (isFavorite) {
                val color = ContextCompat.getColor(context, ThemeHelper.accentColor)
                fav.setColorFilter(color)
                fav.setImageResource(R.drawable.ic_vote_fav)
            } else {
                val color = ContextCompat.getColor(context, R.color.grey_700)
                fav.setColorFilter(color)
                fav.setImageResource(R.drawable.ic_vote_fav_outline)
            }

            fav.visible = true
            fav.setOnClickListener { actionListener.onCommentVoteClicked(item.comment, newVote) }
        }

        if (item.isCollapsed) {
            more.visible = false

            expand.visible = true
            expand.text = "+" + item.hiddenCount
            expand.setOnClickListener { actionListener.expandComment(comment) }

        } else {
            expand.visible = false

            more.visible = true
            more.setOnClickListener { view -> showCommentMenu(view, item) }
        }

        Linkify.linkifyClean(this.content, comment.content, actionListener)
    }

    private fun showCommentMenu(view: View, entry: CommentTree.Item) {
        val userService = view.context.injector.instance<UserService>()

        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.menu_comment)

        popup.menu.findItem(R.id.action_delete_comment)?.let { item ->
            item.isVisible = userService.userIsAdmin
        }

        popup.menu.findItem(R.id.action_collapse)?.let { item ->
            item.isVisible = entry.canCollapse
        }

        popup.setOnMenuItemClickListener { item -> onMenuItemClicked(item, entry) }
        popup.show()
    }

    private fun onMenuItemClicked(item: MenuItem, entry: CommentTree.Item): Boolean {
        val l = actionListener

        when (item.itemId) {
            R.id.action_copy_link -> l.onCopyCommentLink(entry.comment)
            R.id.action_delete_comment -> l.onDeleteCommentClicked(entry.comment)
            R.id.action_collapse -> l.collapseComment(entry.comment)
            R.id.action_report -> l.onReportCommentClicked(entry.comment)
        }

        return true
    }

    interface Listener : Linkify.Callback {
        fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean

        fun onReplyClicked(comment: Api.Comment)

        fun onCommentAuthorClicked(comment: Api.Comment)

        fun onCopyCommentLink(comment: Api.Comment)

        fun onDeleteCommentClicked(comment: Api.Comment): Boolean

        fun onReportCommentClicked(comment: Api.Comment)

        fun collapseComment(comment: Api.Comment)

        fun expandComment(comment: Api.Comment)
    }
}

private fun inflateCommentViewLayout(parent: ViewGroup): CommentSpacerView {
    return parent.layoutInflater.inflate(R.layout.comment_layout, parent, false) as CommentSpacerView
}

class CommentTree(val state: Input) {
    private val byId = state.allComments.associateByTo(hashMapOf()) { it.id }
    private val byParent = state.allComments.groupByTo(hashMapOf()) { it.parent }

    private val depthCache = mutableMapOf<Long, Int>()

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     */
    private val linearizedComments: List<Api.Comment> = run {
        // bring op comments to top, then order by confidence.
        // actually we'll sort in reverse. We use this fact to optimize the
        // linearize step
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
        linearizedComments.map { comment ->
            val depth = depthOf(comment)
            val vote = currentVote(comment)
            val score = commentScore(comment, vote)

            val isCollapsed = comment.id in state.collapsed
            val hiddenCount = if (isCollapsed) countSubTreeComments(comment) else null

            val hasChildren = comment.id in byParent
            val hasOpBadge = state.op == comment.name
            val pointsVisible = state.isAdmin || state.self == comment.name
            val selectedComment = state.selectedCommentId == comment.id

            Item(comment, vote, depth, hasChildren, score, hasOpBadge, hiddenCount,
                    pointsVisible, selectedComment)
        }
    }

    private fun currentVote(comment: Api.Comment): Vote {
        return state.currentVotes[comment.id] ?: Vote.NEUTRAL
    }

    private fun baseVote(comment: Api.Comment): Vote {
        return state.baseVotes.get(comment.id) ?: Vote.NEUTRAL
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
                depthCache[current.parent]?.let { depthOfParent ->
                    return@getOrPut depth + depthOfParent
                }

                // it is not, lets move up the tree
                current = byId[current.parent] ?: break
            }

            return@getOrPut depth
        }
    }

    data class Input(
            val allComments: List<Api.Comment> = listOf(),
            val currentVotes: TLongObjectMap<Vote> = TLongObjectHashMap(),
            val baseVotes: TLongObjectMap<Vote> = TLongObjectHashMap(),
            val collapsed: Set<Long> = setOf(),
            val op: String? = null,
            val self: String? = null,
            val isAdmin: Boolean = false,
            val selectedCommentId: Long = 0L)

    data class Item(val comment: Api.Comment,
                    val vote: Vote,
                    val depth: Int,
                    val hasChildren: Boolean,
                    val currentScore: CommentScore,
                    val hasOpBadge: Boolean,
                    val hiddenCount: Int?,
                    val pointsVisible: Boolean,
                    val selectedComment: Boolean) {

        val commentId: Long get() = comment.id
        val isCollapsed: Boolean get() = hiddenCount != null
        val canCollapse: Boolean get() = hasChildren && hiddenCount == null
    }
}

