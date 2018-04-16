package com.pr0gramm.app.ui.views

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.ConservativeLinearLayoutManager
import com.pr0gramm.app.ui.MergeRecyclerAdapter
import com.pr0gramm.app.ui.SingleViewAdapter
import com.pr0gramm.app.ui.TagCloudLayoutManager
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.layoutInflater
import kotterknife.bindView
import net.danlew.android.joda.DateUtils.getRelativeTimeSpanString
import org.joda.time.Duration
import org.joda.time.Instant
import rx.Observable
import java.lang.Math.min
import kotlin.properties.Delegates.notNull

/**
 */
class InfoLineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val ratingView: TextView by bindView(R.id.rating)
    private val dateView: TextView by bindView(R.id.date)
    private val usernameView: UsernameView by bindView(R.id.username)
    private val tagsView: RecyclerView by bindView(R.id.tags)
    private val voteFavoriteView: Pr0grammIconView by bindView(R.id.action_favorite)
    private val ratingUnknownView: View by bindView(R.id.rating_hidden)
    val voteView: VoteView by bindView(R.id.voting)

    private val settings: Settings? = if (isInEditMode) null else Settings.get()
    private val admin: Boolean = !isInEditMode && context.appKodein().instance<UserService>().userIsAdmin

    private var feedItem: FeedItem? = null
    private var isSelfPost: Boolean = false
    private var tagsAdapter: TagsAdapter by notNull()

    var onVoteListener: (Vote) -> Boolean = { false }
    var onDetailClickedListener: OnDetailClickedListener? = null

    var tagVoteListener: (Api.Tag, Vote) -> Boolean = { _, _ -> false }
    var onAddTagClickedListener: () -> Unit = {}

    init {
        orientation = LinearLayout.VERTICAL

        View.inflate(context, R.layout.post_info_line, this)

        val tagGaps = resources.getDimensionPixelSize(R.dimen.tag_gap_size)
        if (settings != null && settings.tagCloudView) {
            tagsView.itemAnimator = null
            tagsView.layoutManager = TagCloudLayoutManager(tagGaps, tagGaps, 3)
        } else {
            tagsView.itemAnimator = null
            tagsView.layoutManager = ConservativeLinearLayoutManager(getContext(), LinearLayout.HORIZONTAL, false)
        }

        voteView.onVote = { newVote ->
            val changed = onVoteListener(newVote)
            if (changed) {
                updateViewState(newVote)
            }

            changed
        }

        voteFavoriteView.setOnClickListener {
            val currentVote = voteView.vote
            if (currentVote === Vote.FAVORITE) {
                voteView.vote = Vote.UP
            } else {
                voteView.vote = Vote.FAVORITE
            }
        }

        updateTags(emptyMap())
    }

    /**
     * Displays the given [com.pr0gramm.app.feed.FeedItem] along with
     * the given vote.

     * @param item The item to display
     * *
     * @param vote The vote that belongs to the given item.
     */
    fun setFeedItem(item: FeedItem, isSelfPost: Boolean, vote: Observable<Vote>) {
        this.feedItem = item
        this.isSelfPost = isSelfPost

        // update the views!
        usernameView.setUsername(item.user, item.mark)
        dateView.text = getRelativeTimeSpanString(context, item.created)

        usernameView.setOnClickListener {
            onDetailClickedListener?.onUserClicked(item.user)
        }

        vote.subscribe({ v ->
            checkMainThread()
            voteView.setVote(v, true)
            updateViewState(v)
        }, {})
    }

    /**
     * Updates the rating using the currently set feed item and the given vote.

     * @param vote The vote that is currently selected.
     */
    fun updateViewState(vote: Vote) {
        feedItem?.let { feedItem ->
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
    }

    fun updateTags(tags: Map<Api.Tag, Vote>) {
        val sorted = tags.keys.sortedWith(
                compareByDescending<Api.Tag> { it.confidence }.thenBy { it.id })

        val factory = { context: Context ->
            val addTagView = layoutInflater.inflate(R.layout.tags_add, null)
            addTagView.setOnClickListener { onAddTagClickedListener() }
            addTagView
        }

        factory(context).let { view ->
            view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)

            val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(spec, spec)

            val height = view.measuredHeight
            tagsView.minimumHeight = height

            tagsAdapter = TagsAdapter(sorted, tags)
        }

        val adapter = MergeRecyclerAdapter()
        adapter.addAdapter(SingleViewAdapter.of(factory))
        adapter.addAdapter(tagsAdapter)
        tagsView.adapter = adapter
    }

    fun addVote(tag: Api.Tag, vote: Vote) {
        tagsAdapter.updateVote(tag, vote)
    }

    val isOneHourOld: Boolean get() {
        val oneHourAgo = Instant.now().minus(Duration.standardHours(1))
        return feedItem!!.created.isBefore(oneHourAgo)
    }

    private inner class TagsAdapter(tags: List<Api.Tag>, votes: Map<Api.Tag, Vote>) : RecyclerView.Adapter<TagViewHolder>() {
        private val tags = tags.toList()
        private val votes = votes.toMutableMap()
        private val alwaysVoteViews = settings != null && !settings.hideTagVoteButtons
        private var selected = -1

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val view = layoutInflater.inflate(R.layout.tag, parent, false)
            return TagViewHolder(view)
        }

        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            val tag = tags[position]
            holder.tag.text = tag.tag
            holder.tag.setOnClickListener {
                onDetailClickedListener?.onTagClicked(tag)
            }

            if (shouldShowVoteView(position)) {
                holder.vote.setVote(votes[tag] ?: Vote.NEUTRAL, true)
                holder.vote.visibility = View.VISIBLE

                if (!alwaysVoteViews) {
                    holder.tag.setOnLongClickListener {
                        updateSelection(-1)
                        true
                    }
                }

                holder.vote.onVote = { vote -> tagVoteListener(tag, vote) }

            } else {
                holder.vote.visibility = View.GONE
                holder.tag.setOnLongClickListener {
                    updateSelection(position)
                    true
                }
            }
        }

        private fun shouldShowVoteView(position: Int): Boolean {
            return position == selected || alwaysVoteViews
        }

        private fun updateSelection(position: Int) {
            val previousSelected = selected
            selected = position

            notifyItemChanged(Math.max(0, previousSelected))
            notifyItemChanged(selected)
        }

        override fun getItemCount(): Int {
            return tags.size
        }

        override fun getItemId(position: Int): Long {
            return tags[position].id
        }

        internal fun updateVote(tag: Api.Tag, vote: Vote) {
            votes.put(tag, vote)
            notifyDataSetChanged()
        }
    }

    private class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tag: TextView = itemView.find(R.id.tag_text)
        val vote: VoteView = itemView.find(R.id.tag_vote)
    }

    interface OnDetailClickedListener {
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
    }
}
