package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.beloo.widget.chipslayoutmanager.ChipsLayoutManager
import com.beloo.widget.chipslayoutmanager.SpacingItemDecoration
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.ui.base.whileIsAttachedScope
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotterknife.bindOptionalView
import kotterknife.bindView

@SuppressLint("ViewConstructor")
class TagsView(context: Context) : LinearLayout(context) {
    private val recyclerView: RecyclerView by bindView(R.id.tags)
    private val recyclerViewWrapper: TagCloudContainerView? by bindOptionalView(R.id.tags_wrapper)

    private val commentViewStub: ViewStub by bindView(R.id.comment_view_stub)

    // the next views are only accessible if the stub view was inflated
    private val commentBusyIndicator: BusyIndicator by bindView(R.id.busy_indicator)
    private val commentSendView: Button by bindView(R.id.action_send)
    private val commentInputView: LineMultiAutoCompleteTextView by bindView(R.id.tags_comment_input)

    private val adapter = TagsAdapter()

    var actions: PostActions? = null

    private var selectedTagId = -1L
    private var tags: List<Api.Tag> = listOf()
    private var votes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0)
    private var itemId: Long = 0

    private val viewStateCh = ConflatedBroadcastChannel<ViewState>()

    // expose open state as channel
    val viewState = viewStateCh.asFlow().distinctUntilChanged()

    enum class ViewState {
        CLOSED,
        INPUT,
        SENDING,
    }

    private val tagSpacings = object {
        val padding = sp(6)
        val height = context.resources.getDimensionPixelSize(R.dimen.tag_height)

        fun moreOffset(): Int {
            return recyclerView.paddingBottom + dp(8)
        }

        fun clipSize(rows: Int): Int {
            return rows * (height + padding) + recyclerView.paddingTop
        }
    }

    init {
        layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        layoutInflater.inflate(R.layout.post_tags, this, true)
        orientation = LinearLayout.VERTICAL

        if (Settings.get().tagCloudView) {
            addView(layoutInflater.inflate(R.layout.post_tags_cloud, this, false), 0)

            recyclerView.layoutManager = ChipsLayoutManager.newBuilder(context).build()
            recyclerView.addItemDecoration(SpacingItemDecoration(tagSpacings.padding, tagSpacings.padding))

            recyclerViewWrapper?.clipHeight = tagSpacings.clipSize(3)
            recyclerViewWrapper?.moreOffset = tagSpacings.moreOffset()

            recyclerViewWrapper?.updateLayoutParams<MarginLayoutParams> {
                marginStart -= tagSpacings.padding / 2
                marginEnd -= tagSpacings.padding / 2
            }

        } else {
            addView(layoutInflater.inflate(R.layout.post_tags_normal, this, false), 0)

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

        // initialize in normal state
        setViewState(ViewState.CLOSED)

        recyclerView.adapter = adapter

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false
        }

        commentViewStub.setOnInflateListener { stub, inflated ->
            commentSendView.setOnClickListener {
                val text = commentInputView.text.toString().trim()

                if (text.isEmpty()) {
                    showDialog(context) {
                        content(R.string.message_must_not_be_empty)
                        positive()
                    }

                    return@setOnClickListener
                }

                whileIsAttachedScope {
                    setViewState(ViewState.SENDING)
                    try {
                        withContext(NonCancellable) {
                            actions?.writeCommentClicked(text)
                        }

                        commentInputView.setText("")

                    } catch (err: Exception) {
                        if (err !is CancellationException) {
                            ErrorDialogFragment.defaultOnError().call(err)
                        } else {
                            throw err
                        }

                    } finally {
                        setViewState(ViewState.CLOSED)
                    }
                }
            }

            val suggestionService: UserSuggestionService = context.injector.instance()
            commentInputView.setTokenizer(UsernameTokenizer())
            commentInputView.setAdapter(UsernameAutoCompleteAdapter(suggestionService, context,
                    android.R.layout.simple_dropdown_item_1line, "@"))

            commentInputView.setAnchorView(inflated.find(R.id.auto_complete_popup_anchor))

        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return bundleOf(
                "superState" to super.onSaveInstanceState(),
                "viewState" to viewStateCh.value.ordinal
        )
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val viewState = state as Bundle
        super.onRestoreInstanceState(viewState.getParcelable("superState"))

        // restore open/close state
        val stateIdx = state.getInt("viewState", ViewState.CLOSED.ordinal)
        setViewState(ViewState.values()[stateIdx])
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

        if (viewStateCh.value !== ViewState.CLOSED) {
            TextViewCache.addCaching(commentInputView, "tagsView:$itemId")
        }
    }

    private fun updateSelection(id: Long) {
        selectedTagId = id
        rebuildAdapterState()
    }

    private fun rebuildAdapterState() {
        val alwaysShowVoteView = !Settings.get().hideTagVoteButtons

        val lastTag = tags.lastOrNull()

        trace { "Submit list of ${tags.size} items" }
        adapter.submitList(tags.map { tag ->
            val vote = votes[tag.id] ?: Vote.NEUTRAL
            val selected = alwaysShowVoteView || tag.id == selectedTagId
            val lastItem = tag === lastTag
            VotedTag(tag, vote, selected, lastItem, alwaysShowVoteView)
        })
    }

    private fun setViewState(state: ViewState) {
        if (viewStateCh.valueOrNull === state) {
            return
        }

        if (state === ViewState.CLOSED) {
            commentViewStub.isVisible = false

        } else {
            commentViewStub.isVisible = true

            commentBusyIndicator.isVisible = state === ViewState.SENDING

            commentInputView.isVisible = state === ViewState.INPUT
            commentSendView.isVisible = state === ViewState.INPUT

            // might be the first time after inflate
            TextViewCache.addCaching(commentInputView, "tagsView:$itemId")


            for (view in listOf(commentSendView, commentInputView, commentBusyIndicator)) {
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(250).start()
            }
        }

        viewStateCh.offer(state)

        if (state === ViewState.INPUT) {
            recyclerViewWrapper?.reset(animated = true)
        }
    }

    private data class VotedTag(
            val tag: Api.Tag, val vote: Vote = Vote.NEUTRAL,
            val selected: Boolean = false, val lastItem: Boolean = false,
            val alwaysShowVoteView: Boolean = true)

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
                    ButtonViewHolder(parent.layoutInflater.inflate(R.layout.tags_add, parent, false)) {
                        actions?.writeNewTagClicked()
                    }

                viewTypeWriteComment ->
                    WriteCommentViewHolder(parent.layoutInflater.inflate(R.layout.tags_comment, parent, false))

                else -> throw IllegalArgumentException("Unknown view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is TagViewHolder -> holder.set(items[position])
            }
        }
    }

    private inner class ButtonViewHolder(itemView: View, onClick: () -> Unit) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener { onClick() }
        }
    }

    private inner class WriteCommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                when (viewStateCh.value) {
                    ViewState.CLOSED -> setViewState(ViewState.INPUT)
                    ViewState.INPUT -> setViewState(ViewState.CLOSED)
                    else -> Unit
                }
            }

            val buttonView = find<ImageView>(R.id.tag_button)

            itemView.onAttachedScope {
                viewState.collect { viewState ->
                    val color = if (viewState == ViewState.CLOSED) {
                        context.getStyledColor(android.R.attr.textColorSecondary)
                    } else {
                        context.getColorCompat(ThemeHelper.accentColor)
                    }

                    buttonView.imageTintList = ColorStateList.valueOf(color)
                }
            }
        }
    }

    private inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val id = LongValueHolder(0L)
        private val tagView: TextView = itemView.find(R.id.tag_text)
        private val voteView: VoteView = itemView.find(R.id.tag_vote)

        private val lastTagSpacing = context.dp(16)

        private val voteViewWidth = itemView.context.resources.getDimensionPixelSize(R.dimen.tags_tagVoteViewWidth)

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
                if (!voteView.isVisible) {
                    voteView.isVisible = true
                    tagView.updateLayoutParams<MarginLayoutParams> { marginEnd = voteViewWidth }
                }

                voteView.setVoteState(vote, !holderChanged)

                if (!votedTag.alwaysShowVoteView) {
                    tagView.setOnLongClickListener {
                        updateSelection(-1)
                        true
                    }
                }

                voteView.onVote = { newVote -> actions?.voteTagClicked(tag, newVote) == true }

            } else {
                if (voteView.isVisible) {
                    voteView.isVisible = false
                    tagView.updateLayoutParams<MarginLayoutParams> { marginEnd = 0 }
                }

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
