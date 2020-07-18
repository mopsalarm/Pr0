package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.FavedCommentService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.ScrollHideToolbarListener
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.ui.fragments.post.CommentTree
import com.pr0gramm.app.ui.views.CommentSpacerView
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.ui.views.VoteView
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CommentView(parent: ViewGroup) : RecyclerView.ViewHolder(inflateCommentViewLayout(parent)) {
    private val maxLevels = ConfigService.get(itemView.context).commentsMaxLevels

    private val id = LongValueHolder(0L)

    private val vote: VoteView = itemView.find(R.id.voting)
    private val reply: ImageView = itemView.find(R.id.action_reply)
    private val content: TextView = itemView.find(R.id.comment)
    private val senderInfo: SenderInfoView = itemView.find(R.id.sender_info)

    private val more: ImageButton = itemView.find(R.id.action_more)
    private val fav: ImageButton = itemView.find(R.id.action_kfav)

    private var expand: TextView? = null

    private val commentView = itemView as CommentSpacerView

    private var parentScrollView: RecyclerView? = null
    private var parentChain: List<View>? = null

    private var favJob: Job? = null

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        private val minimalScrollSpace = itemView.context.dp(16f)

        private val toolbar = (AndroidUtility.activityFromContext(itemView.context)
                as? ScrollHideToolbarListener.ToolbarActivity)?.scrollHideToolbarListener

        private val statusBarHeight = AndroidUtility.getStatusBarHeight(itemView.context)

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val availableSpace = (content.height - vote.height).toFloat()
            if (availableSpace <= minimalScrollSpace) {
                removeOnScrollListener()
                return
            }

            parentChain?.let { parentChain ->
                var y = 0.0f
                for (view in parentChain) {
                    y += view.translationY + view.top
                }

                if (toolbar != null) {
                    y -= toolbar.visibleHeight.coerceAtLeast(statusBarHeight)
                }

                vote.translationY = (-y).coerceIn(0f, availableSpace)
            }

            super.onScrolled(recyclerView, dx, dy)
        }
    }

    init {
        val grey = itemView.context.getColorCompat(R.color.grey_700)
        val accent = itemView.context.getColorCompat(ThemeHelper.accentColor)

        more.setImageDrawable(DrawableCache.get(R.drawable.ic_more_vert_vec, grey))
        fav.setImageDrawable(DrawableCache.get(R.drawable.ic_vote_fav_outline, grey))
        reply.setImageDrawable(DrawableCache.get(R.drawable.ic_reply_vec, accent))

        itemView.addOnAttachStateChangeListener { isAttached ->
            removeOnScrollListener()

            if (isAttached) {
                val parents = mutableListOf<View>(itemView)
                var parentView: View? = itemView.parent as View?
                while (parentView != null && parentView !is RecyclerView) {
                    parents.add(parentView)
                    parentView = parentView.parent as? View?
                }

                if (parentView is RecyclerView) {
                    parentView.addOnScrollListener(onScrollListener)
                    parentScrollView = parentView
                    parentChain = parents
                }
            }
        }
    }

    private fun removeOnScrollListener() {
        if (parentScrollView != null) {
            parentScrollView?.removeOnScrollListener(onScrollListener)
            parentScrollView = null
            parentChain = null
        }

        vote.translationY = 0f
    }


    @SuppressLint("SetTextI18n")
    fun set(item: CommentTree.Item, actionListener: Listener) {
        val comment = item.comment
        val context = itemView.context

        // skip animations if this  comment changed
        val changed = id.update(comment.id)

        commentView.depth = item.depth
        commentView.spacings = item.spacings

        reply.isVisible = item.depth < maxLevels

        senderInfo.setSenderName(comment.name, comment.mark, item.hasOpBadge)
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
            val color = context.getColorCompat(R.color.selected_comment_background)
            itemView.setBackgroundColor(color)
        } else {
            ViewCompat.setBackground(itemView, null)
        }

        itemView.onAttachedScope {
            favJob?.cancel()

            favJob = launch {
                val service: FavedCommentService = context.injector.instance()

                service.observeCommentIsFaved(comment.id).collect { isFavorite ->
                    if (isFavorite) {
                        val color = context.getColorCompat(ThemeHelper.accentColor)
                        fav.setImageDrawable(DrawableCache.get(R.drawable.ic_vote_fav, color))
                    } else {
                        val color = context.getColorCompat(R.color.grey_700)
                        fav.setImageDrawable(DrawableCache.get(R.drawable.ic_vote_fav_outline, color))
                    }

                    fav.isVisible = true
                    fav.setOnClickListener { actionListener.onCommentFavedClicked(comment, !isFavorite) }
                }
            }
        }

        if (item.isCollapsed) {
            more.isVisible = false

            if (expand == null) {
                expand = itemView.find<ViewStub>(R.id.action_expand_stub).inflate() as TextView
            }

            expand?.let { expand ->
                expand.isVisible = true
                expand.text = "+" + item.hiddenCount
                expand.setOnClickListener { actionListener.expandComment(comment) }
            }

        } else {
            expand?.let { expand ->
                expand.isVisible = false
            }

            more.isVisible = true
            more.setOnClickListener { view -> showCommentMenu(actionListener, view, item) }
        }

        Linkify.linkifyClean(this.content, comment.content, actionListener)
    }

    private fun showCommentMenu(listener: CommentView.Listener, view: View, entry: CommentTree.Item) {
        val userService = view.context.injector.instance<UserService>()

        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.menu_comment)

        popup.menu.findItem(R.id.action_delete_comment)?.let { item ->
            item.isVisible = userService.userIsAdmin
        }

        popup.menu.findItem(R.id.action_block_user)?.let { item ->
            item.isVisible = userService.userIsAdmin
        }

        popup.menu.findItem(R.id.action_collapse)?.let { item ->
            item.isVisible = entry.canCollapse
        }

        popup.setOnMenuItemClickListener { item -> onMenuItemClicked(listener, item, entry) }
        popup.show()
    }

    private fun onMenuItemClicked(l: CommentView.Listener, item: MenuItem, entry: CommentTree.Item): Boolean {
        when (item.itemId) {
            R.id.action_copy_link -> l.onCopyCommentLink(entry.comment)
            R.id.action_delete_comment -> l.onDeleteCommentClicked(entry.comment)
            R.id.action_block_user -> l.onBlockUserClicked(entry.comment)
            R.id.action_collapse -> l.collapseComment(entry.comment)
            R.id.action_report -> l.onReportCommentClicked(entry.comment)
        }

        return true
    }

    interface Listener : Linkify.Callback {
        fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean

        fun onCommentFavedClicked(comment: Api.Comment, faved: Boolean): Boolean

        fun onReplyClicked(comment: Api.Comment)

        fun onCommentAuthorClicked(comment: Api.Comment)

        fun onCopyCommentLink(comment: Api.Comment)

        fun onDeleteCommentClicked(comment: Api.Comment): Boolean

        fun onBlockUserClicked(comment: Api.Comment): Boolean

        fun onReportCommentClicked(comment: Api.Comment)

        fun collapseComment(comment: Api.Comment)

        fun expandComment(comment: Api.Comment)
    }
}

private fun inflateCommentViewLayout(parent: ViewGroup): CommentSpacerView {
    return parent.layoutInflater.inflate(R.layout.comment_layout, parent, false) as CommentSpacerView
}
