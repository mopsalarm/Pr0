package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.support.v7.util.DiffUtil
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.ConservativeLinearLayoutManager
import com.pr0gramm.app.ui.TagCloudLayoutManager
import com.pr0gramm.app.util.*
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import kotterknife.bindView
import org.kodein.di.erased.instance
import java.lang.Math.min

/**
 */
class InfoLineView(context: Context) : LinearLayout(context) {
    private val ratingView: TextView by bindView(R.id.rating)
    private val dateView: TextView by bindView(R.id.date)
    private val usernameView: UsernameView by bindView(R.id.username)
    private val voteFavoriteView: Pr0grammIconView by bindView(R.id.action_favorite)
    private val ratingUnknownView: View by bindView(R.id.rating_hidden)

    private val voteView: VoteView by bindView(R.id.voting)

    private val admin: Boolean = !isInEditMode && context.directKodein.instance<UserService>().userIsAdmin

    private var feedItem: FeedItem? = null
    private var isSelfPost: Boolean = false

    private var updateSubscription by replaceableSubscription()

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

        voteFavoriteView.setOnClickListener {
            val currentVote = voteView.vote
            if (currentVote === Vote.FAVORITE) {
                voteView.doVote(Vote.UP)
            } else {
                voteView.doVote(Vote.FAVORITE)
            }
        }
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

        updateSubscription = ViewUpdater.ofView(dateView, item.created)
                .map {
                    context.getString(R.string.dt_since_label_past, DurationFormat
                            .timeToPointInTime(context, item.created, short = true))
                }
                .subscribe(updateTextView(dateView))

        usernameView.setOnClickListener {
            onDetailClickedListener?.onUserClicked(item.user)
        }

        voteView.setVoteState(vote, animate = false)
        updateViewState(vote)
    }

    /**
     * Updates the rating using the currently set feed item and the given vote.

     * @param vote The vote that is currently selected.
     */
    private fun updateViewState(vote: Vote) {
        val feedItem = this.feedItem ?: return

        if (isOneHourOld || isSelfPost || admin) {
            val rating = feedItem.up - feedItem.down + min(1, vote.voteValue)
            ratingView.text = rating.toString()
            ratingView.setOnLongClickListener {
                Toast.makeText(context,
                        String.format("%d up, %d down", feedItem.up, feedItem.down),
                        Toast.LENGTH_SHORT).show()

                true
            }

            ratingView.visibility = View.VISIBLE
            ratingUnknownView.visibility = View.GONE

        } else {
            ratingUnknownView.visibility = View.VISIBLE
            ratingView.visibility = View.GONE
            ratingView.setOnLongClickListener(null)
        }

        voteFavoriteView.setTextColor(
                if (vote === Vote.FAVORITE) voteView.markedColor else voteView.defaultColor)
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
}

@SuppressLint("ViewConstructor")
class TagsView(context: Context, private val onDetailClickedListener: PostActions) : FrameLayout(context) {
    private val alwaysVoteViews = !Settings.get().hideTagVoteButtons

    private val adapter = TagsAdapter()

    private var selectedTagId = -1L
    private var tags: List<Api.Tag> = listOf()
    private var votes: TLongObjectMap<Vote> = TLongObjectHashMap()

    init {
        View.inflate(context, R.layout.post_tags, this)

        val recyclerView = find<RecyclerView>(R.id.tags)
        recyclerView.adapter = adapter

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

    fun updateTags(tags: List<Api.Tag>, votes: TLongObjectMap<Vote>) {
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
            val vote = votes.get(tag.id) ?: Vote.NEUTRAL
            val selected = alwaysVoteViews || tag.id == selectedTagId
            VotedTag(tag, vote, selected)
        })
    }

    private data class VotedTag(val tag: Api.Tag, val vote: Vote, val selected: Boolean)

    private inner class TagsAdapter : AsyncListAdapter<VotedTag,
            RecyclerView.ViewHolder>(DiffCallback(), detectMoves = true) {

        override fun submitList(newList: List<VotedTag>) {
            val dummyTag = VotedTag(Api.Tag(-2L, 0f, "dummy"), Vote.NEUTRAL, false)
            super.submitList(listOf(dummyTag) + newList)
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
            itemView.setOnClickListener { onDetailClickedListener.writeNewTagClicked() }
        }
    }

    private inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val id = ValueHolder<Long>()
        private val tagView: TextView = itemView.find(R.id.tag_text)
        private val voteView: VoteView = itemView.find(R.id.tag_vote)

        fun set(votedTag: VotedTag) {
            val (tag, vote, selected) = votedTag
            val holderChanged = id.update(tag.id)

            tagView.text = tag.tag
            tagView.setOnClickListener {
                onDetailClickedListener.onTagClicked(tag)
            }

            if (selected) {
                voteView.setVoteState(vote, !holderChanged)
                voteView.visibility = View.VISIBLE

                if (!alwaysVoteViews) {
                    tagView.setOnLongClickListener {
                        updateSelection(-1)
                        true
                    }
                }

                voteView.onVote = { newVote ->
                    onDetailClickedListener.voteTagClicked(tag, newVote)
                }

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
