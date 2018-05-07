package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
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
import com.pr0gramm.app.util.*
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.hash.TLongHashSet
import org.joda.time.Hours
import org.joda.time.Instant.now
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections.emptyList

/**
 */
class CommentsAdapter(
        private val admin: Boolean, private val selfName: String,
        private val actionListener: Listener) : RecyclerView.Adapter<CommentsAdapter.CommentView>() {

    private val logger: Logger = LoggerFactory.getLogger("CommentsAdapter")

    private val scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration())
    private val baseVotes: TLongObjectMap<Vote> = TLongObjectHashMap()

    private var allComments: List<Api.Comment> = emptyList()
    private var visibleComments: List<CommentEntry> = emptyList()

    private var op: String? = null

    // the currently selected comment. Set to update comment
    var selectedCommentId by observeChangeEx(0L) { old, new ->
        visibleComments.forEachIndexed { index, entry ->
            if (entry.comment.id == old || entry.comment.id == new) {
                notifyItemChanged(index)
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    fun updateVotes(votes: TLongObjectMap<Vote>) {
        votes.forEachEntry { id, vote ->
            baseVotes.putIfAbsent(id, vote)
            true
        }

        logger.time("update votes for all comments") {
            val updatedComments = logger.time("... update base vote") {
                visibleComments.map { entry ->
                    val baseVote = baseVotes.get(entry.comment.id) ?: Vote.NEUTRAL
                    entry.copy(vote = baseVote)
                }
            }

            applyUpdatedComments(updatedComments)
        }
    }

    fun updateComments(comments: Collection<Api.Comment>, op: String?) {
        this.op = op
        this.allComments = comments.toList()

        updateVisibleComments()
    }

    private fun updateVisibleComments() {
        logger.time("calculate comment tree") {
            val filteredSortedComments = logger.time("... sort comments") {
                sort(allComments, op)
            }

            val updatedComments = logger.time("... calculate depth") {
                val depths = DepthCalculator(allComments)

                val hasChildren = TLongHashSet(allComments.size).apply {
                    allComments.forEach { add(it.parent) }
                }

                filteredSortedComments.map { comment ->
                    val depth = depths.of(comment)
                    val baseVote = baseVotes.get(comment.id) ?: Vote.NEUTRAL
                    CommentEntry(comment, baseVote, depth, comment.id in hasChildren)
                }
            }

            applyUpdatedComments(updatedComments)
        }
    }

    private fun applyUpdatedComments(updatedComments: List<CommentEntry>) {
        val previousComments = visibleComments

        // calculate difference to apply only update
        val diff = logger.time("... calculate diff") {
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getNewListSize(): Int = updatedComments.size
                override fun getOldListSize(): Int = previousComments.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return previousComments[oldItemPosition].comment.id == updatedComments[newItemPosition].comment.id
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return previousComments[oldItemPosition] == updatedComments[newItemPosition]
                }
            })
        }

        logger.time("... apply diff") {
            visibleComments = updatedComments
            diff.dispatchUpdatesTo(this)
        }
    }

    override fun getItemCount(): Int {
        return visibleComments.size
    }

    override fun getItemId(position: Int): Long {
        return visibleComments[position].comment.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentView {
        return CommentView(LayoutInflater
                .from(parent.context)
                .inflate(R.layout.comment_layout, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: CommentView, position: Int) {
        val entry = visibleComments[position]
        val comment = entry.comment
        val context = holder.itemView.context

        holder.updateCommentDepth(entry.depth)
        holder.senderInfo.setSenderName(comment.name, comment.mark)
        holder.senderInfo.setOnSenderClickedListener {
            actionListener.onCommentAuthorClicked(comment)
        }

        AndroidUtility.linkify(holder.comment, comment.content)

        // show the points
        if (admin
                || equalsIgnoreCase(comment.name, selfName)
                || comment.created.isBefore(scoreVisibleThreshold)) {

            holder.senderInfo.setPoints(getCommentScore(entry))
        } else {
            holder.senderInfo.setPointsUnknown()
        }

        // and the date of the post
        holder.senderInfo.setDate(comment.created)

        // enable or disable the badge
        holder.senderInfo.setBadgeOpVisible(op == comment.name)

        // and register a vote handler
        holder.vote.setVote(entry.vote, true)
        holder.vote.onVote = { vote ->
            val changed = doVote(entry, vote)
            notifyItemChanged(position)
            changed
        }

        // set alpha for the sub views. sadly, setting alpha on view.itemView is not working
        holder.comment.alpha = if (entry.vote === Vote.DOWN) 0.5f else 1f
        holder.senderInfo.alpha = if (entry.vote === Vote.DOWN) 0.5f else 1f

        holder.reply.setOnClickListener {
            actionListener.onAnswerClicked(comment)
        }

//        holder.copyCommentLink.setOnClickListener {
//            commentActionListener?.onCopyCommentLink(comment)
//        }
//
//        holder.copyCommentLink.setOnLongClickListener {
//            commentActionListener?.onDeleteCommentClicked(comment) ?: false
//        }

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
            fav.setOnClickListener {
                doVote(entry, newVote)
                notifyItemChanged(position)
            }
        }

        holder.more.setOnClickListener { view ->
            showCommentMenu(view, entry)
        }
    }

    private fun showCommentMenu(view: View, entry: CommentEntry) {
        val userService = view.context.appKodein().instance<UserService>()

        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.menu_comment)

        popup.menu.findItem(R.id.action_delete_comment)?.let { item ->
            item.isVisible = userService.userIsAdmin
        }

        popup.menu.findItem(R.id.action_collapse)?.let { item ->
            item.isVisible = entry.hasChildren && entry.comment.id !in collapsed
        }

        popup.menu.findItem(R.id.action_expand)?.let { item ->
            item.isVisible = entry.comment.id in collapsed
        }

        popup.setOnMenuItemClickListener { item -> onMenuItemClicked(item, entry) }
        popup.show()
    }

    private fun onMenuItemClicked(item: MenuItem, entry: CommentEntry): Boolean {
        val l = actionListener

        when (item.itemId) {
            R.id.action_copy_link -> l.onCopyCommentLink(entry.comment)
            R.id.action_delete_comment -> l.onDeleteCommentClicked(entry.comment)
            R.id.action_collapse -> collapsed += entry.comment.id
            R.id.action_expand -> collapsed -= entry.comment.id
        }

        return true
    }

    private fun getCommentScore(entry: CommentEntry): CommentScore {
        var score = 0
        score += entry.comment.up - entry.comment.down
        score += entry.vote.voteValue - (baseVotes[entry.comment.id]?.voteValue ?: 0)
        return CommentScore(score, entry.comment.up, entry.comment.down)
    }

    private fun doVote(entry: CommentEntry, vote: Vote): Boolean {
        val performVote = actionListener.onCommentVoteClicked(entry.comment, vote)
        if (performVote) {
            entry.vote = vote
        }

        return performVote
    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     * @param comments The comments to sort
     */
    private fun sort(comments: Collection<Api.Comment>, op: String?): List<Api.Comment> {
        // index all comments by their parent in a multimap.
        val byParent = TLongObjectHashMap<MutableList<Api.Comment>>(comments.size)
        for (comment in comments) {
            val children = byParent[comment.parent] ?: run {
                val newList = mutableListOf<Api.Comment>()
                byParent.put(comment.parent, newList)
                newList
            }

            children.add(comment)
        }

        // sort all comments in byParent
        // bring op comments to top, then order by confidence
        val ordering = compareByDescending<Api.Comment> { it.name == op }.thenByDescending { it.confidence }
        byParent.forEachValue { children ->
            children.sortWith(ordering)
            true
        }

        // now linearize the tree
        val result = mutableListOf<Api.Comment>()
        appendChildComments(result, byParent, 0)
        return result
    }

    private fun appendChildComments(target: MutableList<Api.Comment>,
                                    byParent: TLongObjectHashMap<MutableList<Api.Comment>>,
                                    id: Long) {

        byParent[id]?.takeUnless { id in collapsed }?.forEach { child ->
            if (id !in collapsed) {
                target.add(child)
                appendChildComments(target, byParent, child.id)
            }
        }
    }

    private var collapsed: Set<Long> by observeChange(emptySet()) {
        updateVisibleComments()
    }

    private class DepthCalculator(allComments: List<Api.Comment>) {
        private val byId = allComments.associateBy { it.id }
        private val cache = mutableMapOf<Long, Int>()

        fun of(comment: Api.Comment): Int {
            return cache.getOrPut(comment.id) {
                var current = comment
                var depth = 0

                while (true) {
                    depth++

                    // check if parent is already cached, then we'll take the cached value
                    cache[current.parent]?.let { depthOfParent ->
                        return@getOrPut depth + depthOfParent
                    }

                    // it is not, lets move up the tree
                    current = byId[current.parent] ?: break
                }

                return@getOrPut depth
            }
        }
    }

    class CommentView(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val vote: VoteView = itemView.find(R.id.voting)
        val reply: ImageView = itemView.find(R.id.action_reply)
        val comment: TextView = itemView.find(R.id.comment)
        val senderInfo: SenderInfoView = itemView.find(R.id.sender_info)

        val more: View = itemView.find(R.id.action_more)
        val fav: ImageView = itemView.find(R.id.action_kfav)

        fun updateCommentDepth(depth: Int) {
            val spacerView = itemView as CommentSpacerView
            spacerView.depth = depth

            val maxLevels = ConfigService.get(itemView.context).commentsMaxLevels
            reply.visible = depth < maxLevels
        }
    }

    private data class CommentEntry(val comment: Api.Comment, var vote: Vote, val depth: Int, val hasChildren: Boolean)

    interface Listener {
        fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean

        fun onAnswerClicked(comment: Api.Comment)

        fun onCommentAuthorClicked(comment: Api.Comment)

        fun onCopyCommentLink(comment: Api.Comment)

        fun onDeleteCommentClicked(comment: Api.Comment): Boolean
    }
}
