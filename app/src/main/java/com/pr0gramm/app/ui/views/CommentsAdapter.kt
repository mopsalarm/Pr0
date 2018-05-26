package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Ascii.equalsIgnoreCase
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.*
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.joda.time.Hours
import org.joda.time.Instant.now
import rx.Observable
import kotlin.math.absoluteValue

/**
 */
class CommentsAdapter(
        private val admin: Boolean, private val selfName: String,
        private val actionListener: Listener) : AsyncListAdapter<CommentsAdapter.Entry, CommentView>(ItemCallback()) {

    private val scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration())

    // the currently selected comment. Set to update comment
    var selectedCommentId by observeChangeEx(0L) { _, new ->
        state = state.copy(selectedCommentId = new)
    }

    private var state: State by observeChangeEx(State()) { old, new ->
        if (old != new) {
            updateVisibleComments()
        }
    }

    private var collapsed: Set<Long> by observeChange(setOf()) {
        state = state.copy(collapsed = collapsed)
    }

    init {
        setHasStableIds(true)
    }

    class ItemCallback : DiffUtil.ItemCallback<Entry>() {
        override fun areItemsTheSame(oldItem: Entry, newItem: Entry): Boolean {
            return oldItem.comment.id == newItem.comment.id
        }

        override fun areContentsTheSame(oldItem: Entry, newItem: Entry): Boolean {
            return oldItem == newItem
        }
    }

    fun updateVotes(votes: TLongObjectMap<Vote>) {
        val currentVotes = TLongObjectHashMap(votes)
        val baseVotes = state.baseVotes ?: TLongObjectHashMap(votes)

        state = state.copy(baseVotes = baseVotes, currentVotes = currentVotes)
    }

    fun updateComments(comments: Collection<Api.Comment>, op: String?) {
        state = state.copy(allComments = comments.toList(), op = op)
    }

    private fun updateVisibleComments() {
        val targetState = state

        // do not show
        if (targetState.allComments.isEmpty()) {
            submitList(listOf())
        }

        Observable.fromCallable { CommentTree(targetState).visibleComments }
                .ignoreError()
                .subscribeOnBackground().observeOnMain()
                .subscribe { visibleComments ->
                    // verify that we still got the right state
                    if (state === targetState) {
                        submitList(visibleComments)
                    }
                }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).comment.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentView {
        return CommentView(LayoutInflater
                .from(parent.context)
                .inflate(R.layout.comment_layout, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: CommentView, position: Int) {
        val entry = getItem(position)
        val comment = entry.comment
        val context = holder.itemView.context

        // skip animations if this holders comment changed
        val holderChanged = holder.id.update(comment.id)

        holder.updateCommentDepth(entry.depth)
        holder.senderInfo.setSenderName(comment.name, comment.mark)
        holder.senderInfo.setOnSenderClickedListener {
            actionListener.onCommentAuthorClicked(comment)
        }

        AndroidUtility.linkifyClean(holder.comment, comment.content)

        // show the points
        if (admin
                || equalsIgnoreCase(comment.name, selfName)
                || comment.created.isBefore(scoreVisibleThreshold)) {

            holder.senderInfo.setPoints(entry.currentScore)
        } else {
            holder.senderInfo.setPointsUnknown()
        }

        // and the date of the post
        holder.senderInfo.setDate(comment.created)

        // enable or disable the badge
        holder.senderInfo.setBadgeOpVisible(state.op == comment.name)

        // and register a vote handler
        holder.vote.setVoteState(entry.vote, animate = !holderChanged)
        holder.vote.onVote = { vote -> doVote(entry, vote) }

        // set alpha for the sub views. sadly, setting alpha on view.itemView is not working
        holder.comment.alpha = if (entry.vote === Vote.DOWN) 0.5f else 1f
        holder.senderInfo.alpha = if (entry.vote === Vote.DOWN) 0.5f else 1f

        holder.reply.setOnClickListener {
            actionListener.onAnswerClicked(comment)
        }

        if (comment.id == selectedCommentId) {
            val color = ContextCompat.getColor(context, R.color.selected_comment_background)
            holder.itemView.setBackgroundColor(color)
        } else {
            ViewCompat.setBackground(holder.itemView, null)
        }

        holder.fav.let { fav ->
            val isFavorite = entry.vote == Vote.FAVORITE
            val newVote = if (isFavorite) Vote.UP else Vote.FAVORITE

            if (isFavorite) {
                val color = ContextCompat.getColor(context, ThemeHelper.accentColor)
                fav.setColorFilter(color)
                fav.setImageResource(R.drawable.ic_favorite)
            } else {
                val color = ContextCompat.getColor(context, R.color.grey_700)
                fav.setColorFilter(color)
                fav.setImageResource(R.drawable.ic_favorite_border)
            }

            fav.visible = true
            fav.setOnClickListener {                doVote(entry, newVote)            }
        }

        if (entry.hiddenCount != null) {
            holder.more.visible = false

            holder.expand.visible = true
            holder.expand.text = "+" + entry.hiddenCount
            holder.expand.setOnClickListener {
                collapsed -= comment.id
                notifyItemChanged(position)
            }

        } else {
            holder.expand.visible = false

            holder.more.visible = true
            holder.more.setOnClickListener { view ->
                showCommentMenu(view, entry)
            }
        }
    }

    private fun showCommentMenu(view: View, entry: Entry) {
        val userService = view.context.appKodein().instance<UserService>()

        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.menu_comment)

        popup.menu.findItem(R.id.action_delete_comment)?.let { item ->
            item.isVisible = userService.userIsAdmin
        }

        popup.menu.findItem(R.id.action_collapse)?.let { item ->
            item.isVisible = entry.hasChildren && entry.comment.id !in collapsed
        }

        popup.setOnMenuItemClickListener { item -> onMenuItemClicked(item, entry) }
        popup.show()
    }

    private fun onMenuItemClicked(item: MenuItem, entry: Entry): Boolean {
        val l = actionListener

        when (item.itemId) {
            R.id.action_copy_link -> l.onCopyCommentLink(entry.comment)
            R.id.action_delete_comment -> l.onDeleteCommentClicked(entry.comment)
            R.id.action_collapse -> collapsed += entry.comment.id
        }

        return true
    }

    private fun doVote(entry: Entry, vote: Vote): Boolean {
        return actionListener.onCommentVoteClicked(entry.comment, vote)
    }

    data class State(
            val allComments: List<Api.Comment> = listOf(),
            val currentVotes: TLongObjectMap<Vote> = TLongObjectHashMap(),
            val baseVotes: TLongObjectMap<Vote>? = null,
            val collapsed: Set<Long> = setOf(),
            val op: String? = null,
            val selectedCommentId: Long = 0L)

    data class Entry(val comment: Api.Comment, val vote: Vote, val depth: Int,
                     val hasChildren: Boolean, val currentScore: CommentScore,
                     val hasOpBadge: Boolean, val hiddenCount: Int?)

    interface Listener {
        fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean

        fun onAnswerClicked(comment: Api.Comment)

        fun onCommentAuthorClicked(comment: Api.Comment)

        fun onCopyCommentLink(comment: Api.Comment)

        fun onDeleteCommentClicked(comment: Api.Comment): Boolean
    }
}

class CommentView(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val id = ValueHolder(0L)

    val vote: VoteView = itemView.find(R.id.voting)
    val reply: ImageView = itemView.find(R.id.action_reply)
    val comment: TextView = itemView.find(R.id.comment)
    val senderInfo: SenderInfoView = itemView.find(R.id.sender_info)

    val more: View = itemView.find(R.id.action_more)
    val fav: ImageView = itemView.find(R.id.action_kfav)
    val expand: TextView = itemView.find(R.id.action_expand)

    fun updateCommentDepth(depth: Int) {
        val spacerView = itemView as CommentSpacerView
        spacerView.depth = depth

        val maxLevels = ConfigService.get(itemView.context).commentsMaxLevels
        reply.visible = depth < maxLevels
    }
}

private class CommentTree(val state: CommentsAdapter.State) {
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

    val visibleComments: List<CommentsAdapter.Entry> = run {
        linearizedComments.map { comment ->
            val depth = depthOf(comment)
            val vote = currentVote(comment)
            val score = commentScore(comment, vote)

            val isCollapsed = comment.id in state.collapsed
            val hiddenCount = if (isCollapsed) countSubTreeComments(comment) else null

            val hasChildren = comment.id in byParent
            val hasOpBadge = state.op == comment.name

            CommentsAdapter.Entry(comment, vote, depth, hasChildren, score, hasOpBadge, hiddenCount)
        }
    }

    private fun currentVote(comment: Api.Comment): Vote {
        return state.currentVotes[comment.id] ?: Vote.NEUTRAL
    }

    private fun baseVote(comment: Api.Comment): Vote {
        return state.baseVotes?.get(comment.id) ?: Vote.NEUTRAL
    }

    private fun commentScore(comment: Api.Comment, currentVote: Vote): CommentScore {
        val delta = currentVote.voteValue - baseVote(comment).voteValue

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
}
