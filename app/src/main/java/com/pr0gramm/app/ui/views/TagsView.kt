package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.chlm.ChipsLayoutManager
import ir.mahdiparastesh.chlm.SpacingItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.PostTagsCloudBinding
import com.pr0gramm.app.databinding.PostTagsNormalBinding
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.ConservativeLinearLayoutManager
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.util.*

@SuppressLint("ViewConstructor")
class TagsView(context: Context) : LinearLayout(context) {
    private val recyclerView: RecyclerView
    private val recyclerViewWrapper: TagCloudContainerView?

    private val adapter = TagsAdapter()

    var actions: PostActions? = null

    private var selectedTagId = -1L
    private var tags: List<Api.Tag> = listOf()
    private var votes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0)
    private var itemId: Long = 0

    init {
        layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        orientation = LinearLayout.VERTICAL

        if (Settings.tagCloudView) {
            val views = PostTagsCloudBinding.inflate(layoutInflater, this, true)
            recyclerView = views.tagsRecyclerView
            recyclerViewWrapper = views.tagsWrapper

            val tagSpacings = object {
                val padding = sp(6)
                val height = context.resources.getDimensionPixelSize(R.dimen.tag_height)

                fun moreOffset(): Int {
                    return recyclerView.paddingBottom + dp(8)
                }

                fun clipSize(rows: Int): Int {
                    return rows * (height + padding) + recyclerView.paddingTop
                }
            }

            recyclerView.layoutManager = ChipsLayoutManager.newBuilder(context).build()
            recyclerView.addItemDecoration(SpacingItemDecoration(tagSpacings.padding, tagSpacings.padding))

            recyclerViewWrapper.clipHeight = tagSpacings.clipSize(3)
            recyclerViewWrapper.moreOffset = tagSpacings.moreOffset()

            recyclerViewWrapper.updateLayoutParams<MarginLayoutParams> {
                marginStart -= tagSpacings.padding / 2
                marginEnd -= tagSpacings.padding / 2
            }

        } else {
            val views = PostTagsNormalBinding.inflate(layoutInflater, this, true)
            recyclerView = views.tagsRecyclerView
            recyclerViewWrapper = null

            recyclerView.layoutManager = ConservativeLinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
            recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                val spacing = dp(6)
                val spacingFirstItem = dp(16)

                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.setEmpty()

                    val index = parent.getChildAdapterPosition(view)
                    outRect.left = if (index == 0) spacingFirstItem else spacing
                }
            })
        }

        recyclerView.adapter = adapter

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false
        }
    }

    fun updateTags(itemId: Long, tags: List<Api.Tag>, votes: LongSparseArray<Vote>) {
        if (this.tags != tags || this.votes != votes || tags.isEmpty()) {
            this.tags = tags
            this.votes = votes
            rebuildAdapterState()
        }

        if (this.itemId != itemId) {
            this.itemId = itemId

            // the view was recycled, so we need to scroll back to the beginning of the list.
            if (adapter.itemCount > 0) {
                recyclerView.scrollToPosition(0)
            }

            recyclerViewWrapper?.reset()
        }
    }

    private fun updateSelection(id: Long) {
        selectedTagId = id
        rebuildAdapterState()
    }

    private fun rebuildAdapterState() {
        val alwaysShowVoteView = !Settings.hideTagVoteButtons

        val lastTag = tags.lastOrNull()

        val votedTags = tags.map { tag ->
            val vote = votes[tag.id] ?: Vote.NEUTRAL
            val selected = alwaysShowVoteView || tag.id == selectedTagId
            val lastItem = tag === lastTag
            VotedTag(tag, vote, selected, lastItem, alwaysShowVoteView)
        }

        trace { "Submit list of ${tags.size} items" }
        adapter.submitList(votedTags)
    }

    private data class VotedTag(
            val tag: Api.Tag, val vote: Vote = Vote.NEUTRAL,
            val selected: Boolean = false, val lastItem: Boolean = false,
            val alwaysShowVoteView: Boolean = true)

    private inner class TagsAdapter : AsyncListAdapter<VotedTag,
            TagViewHolder>(DiffCallback(), detectMoves = true) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            return TagViewHolder(parent.layoutInflater.inflate(R.layout.tag, parent, false))
        }

        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            holder.set(items[position])
        }
    }

    private inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val id = LongValueHolder(0L)
        private val tagView: TextView = itemView.find(R.id.tag_text)

        private val upView = find<ImageButton>(R.id.vote_up)
        private val downView = find<ImageButton>(R.id.vote_down)

        private val voteController = VoteViewController(
                upView, downView,
                activeScale = 1.0f, inactiveScale = 0.9f,
        )

        private val lastTagSpacing = context.dp(16)

        fun set(votedTag: VotedTag) {
            val (tag, vote, selected) = votedTag
            val holderChanged = id.update(tag.id)

            tagView.text = tag.text
            tagView.setOnClickListener {
                actions?.onTagClicked(tag)
            }

            // mark tags based on their confidence.
            itemView.alpha = if (votedTag.tag.confidence < 0.2) 0.8f else 1.0f

            voteController.updateVote(vote, animate = !holderChanged)

            voteController.onVoteClicked = { newVote ->
                actions?.voteTagClicked(tag, newVote) == true
            }

            if (selected) {
                upView.isVisible = true
                downView.isVisible = true

                voteController.updateVote(vote, !holderChanged)

                if (!votedTag.alwaysShowVoteView) {
                    tagView.setOnLongClickListener {
                        updateSelection(-1)
                        true
                    }
                }
            } else {
                upView.isVisible = false
                downView.isVisible = false

                if (!votedTag.alwaysShowVoteView) {
                    tagView.setOnLongClickListener {
                        updateSelection(tag.id)
                        true
                    }
                }
            }

            if (votedTag.alwaysShowVoteView || selected) {
                tagView.setOnLongClickListener {
                    val text = votedTag.tag.text

                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(text, text))

                    Snackbar.make(itemView, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.okay) { /* do nothing */ }
                            .configureNewStyle()
                            .show()

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
