package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.ScrollHideToolbarListener
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.ui.fragments.post.CommentTree
import com.pr0gramm.app.ui.views.CommentSpacerView
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.ui.views.VoteViewController
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CommentView(parent: ViewGroup) : RecyclerView.ViewHolder(inflateCommentViewLayout(parent)) {
    private val maxLevels = ConfigService.get(itemView.context).commentsMaxLevels

    private val id = LongValueHolder(0L)

    private val inactiveColor = itemView.context.getStyledColor(android.R.attr.textColorSecondary)
    private val accentColor = itemView.context.getStyledColor(android.R.attr.colorAccent)

    private val replyView: ImageView = find(R.id.action_reply)
    private val contentView: TextView = find(R.id.comment)
    private val senderInfoView: SenderInfoView = find(R.id.sender_info)

    private val moreView: ImageButton = find(R.id.action_more)
    private val favView: ImageButton = find(R.id.action_kfav)

    private val upVoteView: ImageButton = find(R.id.vote_up)
    private val downVoteView: ImageButton = find(R.id.vote_down)
    private val voteController = VoteViewController(upVoteView, downVoteView)

    private val expandContainerView = find<View>(R.id.action_expand)
    private val expandView: TextView = expandContainerView.find(R.id.action_expand_info)

    private val commentSpacerView = itemView as CommentSpacerView

    private var parentScrollView: RecyclerView? = null
    private var parentChain: List<View>? = null

    private var favJob: Job? = null

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        private val minimalScrollSpace = itemView.context.dp(16f)

        @SuppressLint("StaticFieldLeak")
        private val toolbar = (AndroidUtility.activityFromContext(itemView.context)
                as? ScrollHideToolbarListener.ToolbarActivity)?.scrollHideToolbarListener

        private val statusBarHeight = AndroidUtility.getStatusBarHeight(itemView.context)

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val voteViewHeight = upVoteView.height + downVoteView.height

            val availableSpace = (contentView.height - voteViewHeight).toFloat()
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

                // translate vote views to keep in scroll window
                upVoteView.translationY = (-y).coerceIn(0f, availableSpace)
                downVoteView.translationY = (-y).coerceIn(0f, availableSpace)
            }

            super.onScrolled(recyclerView, dx, dy)
        }
    }

    init {
        moreView.setImageDrawable(DrawableCache.get(R.drawable.ic_more_vert_vec, inactiveColor))
        favView.setImageDrawable(DrawableCache.get(R.drawable.ic_vote_fav_outline, inactiveColor))
        replyView.setImageDrawable(DrawableCache.get(R.drawable.ic_reply_vec, inactiveColor))

        itemView.addOnAttachStateChangeListener { isAttached ->
            removeOnScrollListener()

            if (isAttached) {
                val parents = mutableListOf(itemView)
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

        upVoteView.translationY = 0f
        downVoteView.translationY = 0f
    }


    @SuppressLint("SetTextI18n")
    fun set(item: CommentTree.Item, actionListener: Listener) {
        val comment = item.comment
        val context = itemView.context

        // skip animations if this  comment changed
        val changed = id.update(comment.id)

        commentSpacerView.depth = item.depth - 1
        commentSpacerView.spacings = item.spacings

        replyView.isVisible = item.depth < maxLevels

        senderInfoView.setSenderName(comment.name, comment.mark, item.hasOpBadge)
        senderInfoView.setOnSenderClickedListener {
            actionListener.onCommentAuthorClicked(comment)
        }

        // show the points
        val scoreVisibleThreshold = Instant.now() - Duration.hours(1)
        if (item.pointsVisible || comment.created.isBefore(scoreVisibleThreshold)) {
            senderInfoView.setPoints(item.currentScore)
        } else {
            senderInfoView.setPointsUnknown()
        }

        // and the date of the post
        senderInfoView.setDate(comment.created)

        // and register a vote handler
        voteController.updateVote(item.vote, animate = !changed)
        voteController.onVoteClicked = { vote -> actionListener.onCommentVoteClicked(item.comment, vote) }

        if (item.vote === Vote.DOWN) {
            contentView.setTextColor(context.getStyledColor(android.R.attr.textColorSecondary))
            senderInfoView.alpha = 0.5f
        } else {
            contentView.setTextColor(context.getStyledColor(android.R.attr.textColorPrimary))
            senderInfoView.alpha = 1f
        }

        replyView.setOnClickListener {
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
                        favView.setImageDrawable(DrawableCache.get(R.drawable.ic_vote_fav, accentColor))
                    } else {
                        favView.setImageDrawable(DrawableCache.get(R.drawable.ic_vote_fav_outline, inactiveColor))
                    }

                    favView.isVisible = true
                    favView.setOnClickListener { actionListener.onCommentFavedClicked(comment, !isFavorite) }
                }
            }
        }

        moreView.setOnClickListener { view -> showCommentMenu(actionListener, view, item) }

        expandContainerView.isVisible = item.isCollapsed

        if (item.isCollapsed) {
            expandContainerView.setOnClickListener { actionListener.expandComment(comment) }
            expandView.text = "+" + item.hiddenCount
        }

        Linkify.linkifyClean(this.contentView, comment.content, actionListener)
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
