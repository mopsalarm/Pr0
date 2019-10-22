package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.FollowState
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.FollowAction
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.ConservativeLinearLayoutManager
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.TagCloudLayoutManager
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

        updateFollowState(followState = FollowState.Impl(0, false, false))
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
                when (selectedItemId) {
                    R.id.action_follow_off -> {
                        updateFollowState(FollowState.Impl(0, false, false))
                        onDetailClickedListener?.updateFollowUser(FollowAction.NONE)
                    }
                    R.id.action_follow_normal -> {
                        updateFollowState(FollowState.Impl(0, true, false))
                        onDetailClickedListener?.updateFollowUser(FollowAction.FOLLOW)
                    }
                    R.id.action_follow_full -> {
                        updateFollowState(FollowState.Impl(0, true, true))
                        onDetailClickedListener?.updateFollowUser(FollowAction.SUBSCRIBED)
                    }
                }
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
    fun writeCommentClicked(text: String): Boolean

    /**
     * Follow the user
     */
    suspend fun updateFollowUser(follow: FollowAction)
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

        if (Settings.get().tagCloudView) {
            val tagGaps = resources.getDimensionPixelSize(R.dimen.tag_gap_size)
            recyclerView.layoutManager = TagCloudLayoutManager(tagGaps, tagGaps, 3)
            recyclerView.itemAnimator = null
        } else {
            recyclerView.layoutManager = ConservativeLinearLayoutManager(getContext(), LinearLayout.HORIZONTAL, false)

            recyclerView.itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
            }

        }
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
        adapter.submitList(tags.map { tag ->
            val vote = votes[tag.id] ?: Vote.NEUTRAL
            val selected = alwaysVoteViews || tag.id == selectedTagId
            VotedTag(tag, vote, selected)
        })
    }

    private data class VotedTag(val tag: Api.Tag, val vote: Vote, val selected: Boolean)

    private inner class TagsAdapter : AsyncListAdapter<VotedTag,
            RecyclerView.ViewHolder>(DiffCallback(), name = "TagAdapter", detectMoves = true) {

        override fun submitList(newList: List<VotedTag>, forceSync: Boolean) {
            val dummyTag = VotedTag(Api.Tag(-2L, 0f, "dummy"), Vote.NEUTRAL, false)
            super.submitList(listOf(dummyTag) + newList, forceSync)
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> ButtonHolder(parent.layoutInflater.inflate(R.layout.tags_add, parent, false))
                1 -> TagViewHolder(parent.layoutInflater.inflate(R.layout.tag, parent, false))
                else -> throw IllegalArgumentException("Unknown view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is TagViewHolder -> holder.set(items[position])
            }
        }
    }

    private inner class ButtonHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener { actions?.writeNewTagClicked() }
        }
    }

    private inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val id = LongValueHolder(0L)
        private val tagView: TextView = itemView.find(R.id.tag_text)
        private val voteView: VoteView = itemView.find(R.id.tag_vote)

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
