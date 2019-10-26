package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.text.style.ImageSpan
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.FollowState
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.ConservativeLinearLayoutManager
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotterknife.bindView
import kotlin.math.min

/**
 */
class InfoLineView(context: Context) : LinearLayout(context) {
    private val dateView: TextView by bindView(R.id.date)
    private val usernameView: UsernameView by bindView(R.id.username)
    private val voteView: VoteView by bindView(R.id.voting)
    private val followStateView: ImageView by bindView(R.id.action_follow)

    private val drawableCache = DrawableCache()

    private val admin: Boolean = !isInEditMode && context.injector.instance<UserService>().userIsAdmin

    private var feedItem: FeedItem? = null
    private var isSelfPost: Boolean = false

    var onDetailClickedListener: PostActions? = null

    init {
        orientation = LinearLayout.VERTICAL

        inflate(context, R.layout.post_info_line, this)

        voteView.onVote = { newVote ->
            val changed = onDetailClickedListener?.votePostClicked(newVote) ?: false
            if (changed) {
                updateViewState(newVote)
            }

            changed
        }

        followStateView.setOnClickListener {
            val popup = PopupMenu(context, followStateView)
            MenuInflater(context).inflate(R.menu.menu_follow, popup.menu)
            popup.setOnMenuItemClickListener { followMenuClicked(it.itemId); true }
            popup.show()
        }

        updateFollowState(followState = FollowState.NONE)
    }

    /**
     * Displays the given [com.pr0gramm.app.feed.FeedItem] along with
     * the given vote.

     * @param item The item to display
     * *
     * @param vote The vote that belongs to the given item.
     */
    fun setFeedItem(item: FeedItem, isSelfPost: Boolean, vote: Vote) {
        this.feedItem = item
        this.isSelfPost = isSelfPost

        // update the views!
        usernameView.setUsername(item.user, item.mark)

        usernameView.setOnClickListener {
            onDetailClickedListener?.onUserClicked(item.user)
        }

        voteView.setVoteState(vote, animate = false)

        updateViewState(vote)
    }

    private fun formatScore(vote: Vote): String {
        val feedItem = this.feedItem ?: return ""

        if (isOneHourOld || isSelfPost || admin) {
            val rating = feedItem.up - feedItem.down + min(1, vote.voteValue)
            return rating.toString()

        } else {
            return "\u2022\u2022\u2022"
        }
    }

    /**
     * Updates the rating using the currently set feed item and the given vote.

     * @param vote The vote that is currently selected.
     */
    @SuppressLint("SetTextI18n")
    private fun updateViewState(vote: Vote) {
        val feedItem = this.feedItem ?: return

        val viewVisibility = if (feedItem.deleted) View.INVISIBLE else View.VISIBLE

        // also hide the vote view.
        voteView.visibility = viewVisibility

        val textColor = dateView.currentTextColor

        val dClock = drawableCache.get(R.drawable.ic_clock, textColor).mutate()
        dClock.setBounds(0, 0, context.dip2px(12), context.dip2px(12))

        val dPlus = drawableCache.get(R.drawable.ic_plus, textColor)
        dPlus.setBounds(0, 0, context.dip2px(12), context.dip2px(12))

        ViewUpdater.replaceText(dateView, feedItem.created) {
            val date = DurationFormat.timeSincePastPointInTime(context, feedItem.created, short = true)
            val score = formatScore(vote)

            buildSpannedString {
                append(date)
                append("\u2009")
                inSpans(ImageSpan(dClock, ImageSpan.ALIGN_BOTTOM)) {
                    append(" ")
                }

                if (score.isNotEmpty()) {
                    append("   ")
                    append(score)
                    append("\u2009")
                    inSpans(ImageSpan(dPlus, ImageSpan.ALIGN_BOTTOM)) {
                        append(" ")
                    }
                }
            }
        }
    }

    private fun followMenuClicked(selectedItemId: Int) {
        requireBaseActivity().launchWithErrorHandler {
            followStateView.isEnabled = false
            try {
                val state = when (selectedItemId) {
                    R.id.action_follow_normal -> FollowState.FOLLOW
                    R.id.action_follow_full -> FollowState.SUBSCRIBED
                    else -> FollowState.NONE
                }

                // update view
                updateFollowState(state)

                // publish follow state to backend
                onDetailClickedListener?.updateFollowUser(state)

            } finally {
                followStateView.isEnabled = true
            }
        }
    }

    fun updateFollowState(followState: FollowState) {
        when {
            followState.subscribed -> {
                val color = context.getColorCompat(ThemeHelper.accentColor)
                followStateView.setImageDrawable(drawableCache.get(R.drawable.ic_action_follow_full, color))
            }

            followState.following -> {
                val color = context.getColorCompat(ThemeHelper.accentColor)
                followStateView.setImageDrawable(drawableCache.get(R.drawable.ic_action_follow_normal, color))
            }

            else -> {
                val color = context.getStyledColor(android.R.attr.textColorSecondary)
                followStateView.setImageDrawable(drawableCache.get(R.drawable.ic_action_follow_off, color))
            }
        }
    }

    private val isOneHourOld: Boolean
        get() {
            val oneHourAgo = Instant.now() - Duration.hours(1)
            return feedItem?.created?.isBefore(oneHourAgo) ?: false
        }
}

interface PostActions {
    /**
     * Called if the user clicked on a tag.

     * @param tag The tag that was clicked.
     */
    fun onTagClicked(tag: Api.Tag)

    /**
     * Called if a user clicks on a username
     * @param username The username that was clicked.
     */
    fun onUserClicked(username: String)

    /**
     * The User wants to vote this tag.
     */
    fun voteTagClicked(tag: Api.Tag, vote: Vote): Boolean

    /**
     * The user wants to vote this post
     */
    fun votePostClicked(vote: Vote): Boolean

    /**
     * The user wants to write a new tag.
     */
    fun writeNewTagClicked()

    /**
     * Writes a new comment
     */
    fun writeCommentClicked()

    /**
     * Follow the user
     */
    suspend fun updateFollowUser(follow: FollowState)
}

@SuppressLint("ViewConstructor")
class TagsView(context: Context) : FrameLayout(context) {
    private val alwaysVoteViews = !Settings.get().hideTagVoteButtons

    private val recyclerView: RecyclerView by bindView(R.id.tags)
    private val adapter = TagsAdapter()

    var actions: PostActions? = null

    private var selectedTagId = -1L
    private var tags: List<Api.Tag> = listOf()
    private var votes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0)

    init {
        View.inflate(context, R.layout.post_tags, this)

        recyclerView.layoutManager = ConservativeLinearLayoutManager(getContext(), LinearLayout.HORIZONTAL, false)

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false
        }

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            val spacing = context.dip2px(8)
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.setEmpty()

                val index = parent.getChildAdapterPosition(view)
                outRect.left = if (index == 0) 2 * spacing else spacing
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        recyclerView.adapter = adapter
    }

    override fun onDetachedFromWindow() {
        recyclerView.adapter = null
        super.onDetachedFromWindow()
    }

    fun updateTags(tags: List<Api.Tag>, votes: LongSparseArray<Vote>) {
        this.tags = tags
        this.votes = votes
        rebuildAdapterState()
    }

    private fun updateSelection(id: Long) {
        selectedTagId = id
        rebuildAdapterState()
    }

    private fun rebuildAdapterState() {
        val lastTag = tags.lastOrNull()

        adapter.submitList(tags.map { tag ->
            val vote = votes[tag.id] ?: Vote.NEUTRAL
            val selected = alwaysVoteViews || tag.id == selectedTagId
            val lastItem = tag === lastTag
            VotedTag(tag, vote, selected, lastItem)
        })
    }

    private data class VotedTag(val tag: Api.Tag, val vote: Vote = Vote.NEUTRAL, val selected: Boolean = false, val lastItem: Boolean = false)

    private inner class TagsAdapter : AsyncListAdapter<VotedTag,
            RecyclerView.ViewHolder>(DiffCallback(), name = "TagAdapter", detectMoves = true) {

        private val viewTypeWriteComment = 0
        private val viewTypeWriteTag = 1
        private val viewTypeTag = 2

        private val placeholders = listOf(
                VotedTag(Api.Tag(-3L, 0f, "placeholder write comment")),
                VotedTag(Api.Tag(-2L, 0f, "placeholder write tag")))


        override fun submitList(newList: List<VotedTag>, forceSync: Boolean) {
            super.submitList(placeholders + newList, forceSync)
        }

        override fun getItemViewType(position: Int): Int {
            return position.coerceAtMost(placeholders.size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                viewTypeTag ->
                    TagViewHolder(parent.layoutInflater.inflate(R.layout.tag, parent, false))

                viewTypeWriteTag ->
                    ButtonHolder(parent.layoutInflater.inflate(R.layout.tags_add, parent, false)) {
                        actions?.writeNewTagClicked()
                    }

                viewTypeWriteComment ->
                    ButtonHolder(parent.layoutInflater.inflate(R.layout.tags_comment, parent, false)) {
                        actions?.writeCommentClicked()
                    }

                else -> throw IllegalArgumentException("Unknown view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is TagViewHolder -> holder.set(items[position])
            }
        }
    }

    private inner class ButtonHolder(itemView: View, onClick: () -> Unit) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener { onClick() }
        }
    }

    private inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val id = LongValueHolder(0L)
        private val tagView: TextView = itemView.find(R.id.tag_text)
        private val voteView: VoteView = itemView.find(R.id.tag_vote)

        private val lastTagSpacing = context.dip2px(16)

        fun set(votedTag: VotedTag) {
            val (tag, vote, selected) = votedTag
            val holderChanged = id.update(tag.id)

            tagView.text = tag.tag
            tagView.setOnClickListener {
                actions?.onTagClicked(tag)
            }

            // mark tags based on their confidence.
            itemView.alpha = if (votedTag.tag.confidence < 0.2) 0.8f else 1.0f

            if (selected) {
                voteView.setVoteState(vote, !holderChanged)
                voteView.visibility = View.VISIBLE

                if (!alwaysVoteViews) {
                    tagView.setOnLongClickListener {
                        updateSelection(-1)
                        true
                    }
                }

                voteView.onVote = { newVote -> actions?.voteTagClicked(tag, newVote) == true }

            } else {
                voteView.visibility = View.GONE
                tagView.setOnLongClickListener {
                    updateSelection(tag.id)
                    true
                }
            }

            itemView.updateLayoutParams<MarginLayoutParams> {
                rightMargin = if (votedTag.lastItem) lastTagSpacing else 0
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<VotedTag>() {
        override fun areItemsTheSame(oldItem: VotedTag, newItem: VotedTag): Boolean {
            return oldItem.tag.id == newItem.tag.id
        }

        override fun areContentsTheSame(oldItem: VotedTag, newItem: VotedTag): Boolean {
            return oldItem == newItem
        }
    }
}
