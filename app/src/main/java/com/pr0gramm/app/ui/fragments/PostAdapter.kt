package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.support.annotation.LayoutRes
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.views.CommentPostLine
import com.pr0gramm.app.ui.views.InfoLineView
import com.pr0gramm.app.ui.views.PostActions
import com.pr0gramm.app.ui.views.TagsView
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.removeFromParent
import gnu.trove.map.TLongObjectMap
import java.util.*


private enum class Offset(val offset: Long, val type: Class<out PostAdapter.Item>) {
    Placeholder(0, PostAdapter.Item.PlaceholderItem::class.java),
    Info(1, PostAdapter.Item.InfoItem::class.java),
    Tags(2, PostAdapter.Item.TagsItem::class.java),
    CommentInputItem(3, PostAdapter.Item.CommentInputItem::class.java),
    CommentsLoadingItem(4, PostAdapter.Item.CommentsLoadingItem::class.java),
    CommentItem(1000, PostAdapter.Item.CommentItem::class.java)
}

class PostAdapter(
        private val commentViewListener: CommentView.Listener,
        private val postActions: PostActions)
    : AsyncListAdapter<PostAdapter.Item, RecyclerView.ViewHolder>(ItemCallback()) {

    init {
        setHasStableIds(true)

        if (Offset.values().size != Offset.values().map { it.offset }.distinct().size)
            throw IllegalArgumentException("Error in Offset() mapping")

        if (Offset.values().size != Offset.values().map { it.type }.distinct().size)
            throw IllegalArgumentException("Error in Offset() mapping")
    }

    private val viewTypesByType = IdentityHashMap(Offset.values().associateBy { it.type })
    private val viewTypesByIndex = Offset.values()

    override fun getItemViewType(position: Int): Int {
        val type = items[position].javaClass
        return viewTypesByType.getValue(type).ordinal
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context

        return when (viewTypesByIndex[viewType]) {
            Offset.Placeholder ->
                PlaceholderHolder(PlaceholderView(context))

            Offset.Info ->
                InfoLineViewHolder(postActions, InfoLineView(context))

            Offset.Tags ->
                TagsViewHolder(TagsView(context, postActions))

            Offset.CommentInputItem ->
                CommentPostLineHolder(postActions, CommentPostLine(context))

            Offset.CommentItem ->
                CommentView(parent, commentViewListener)

            Offset.CommentsLoadingItem ->
                StaticViewHolder(parent, R.layout.comments_are_loading)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is CommentView ->
                holder.set((item as Item.CommentItem).commentTreeItem)

            is PlaceholderHolder ->
                holder.set(item as Item.PlaceholderItem)

            is InfoLineViewHolder ->
                holder.set(item as Item.InfoItem)

            is TagsViewHolder ->
                holder.set(item as Item.TagsItem)

            is CommentPostLineHolder ->
                holder.set(item as Item.CommentInputItem)
        }
    }

    sealed class Item(val id: Long) {
        class PlaceholderItem(val height: Int,
                              val viewer: View,
                              val mediaControlsContainer: View?) : Item(Offset.Placeholder.offset) {

            override fun hashCode(): Int = height
            override fun equals(other: Any?): Boolean = other is PlaceholderItem && other.height == height
        }

        data class InfoItem(
                val item: FeedItem,
                val vote: Vote,
                val isOurPost: Boolean) : Item(Offset.Info.offset)

        data class TagsItem(val tags: List<Api.Tag>, val votes: TLongObjectMap<Vote>)
            : Item(Offset.Tags.offset)

        data class CommentInputItem(val text: String)
            : Item(Offset.CommentInputItem.offset)

        data class CommentItem(val commentTreeItem: CommentTree.Item)
            : Item(Offset.CommentItem.offset + commentTreeItem.comment.id)

        object CommentsLoadingItem
            : Item(Offset.CommentsLoadingItem.offset)
    }

    private class ItemCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem == newItem
        }
    }
}

private class PlaceholderHolder(val pv: PlaceholderView) : RecyclerView.ViewHolder(pv) {
    fun set(item: PostAdapter.Item.PlaceholderItem) {
        pv.viewer = item.viewer
        pv.fixedHeight = item.height

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            pv.requestLayout()
        } else {
            // it looks like a requestLayout is not honored on pre kitkat devices
            // if already in a layout pass.
            pv.post { pv.requestLayout() }
        }

        if (item.mediaControlsContainer != null) {
            // only move media controls if they are attached to a different placeholder view.
            // the reason to do so is that we could just have received an update after the
            // controls were attached to a player in fullscreen.
            if (pv.parent !== pv && (pv.parent == null || pv.parent is PlaceholderView)) {
                item.mediaControlsContainer.removeFromParent()
                pv.addView(item.mediaControlsContainer)
            }
        }
    }
}

private class TagsViewHolder(val tagsView: TagsView) : RecyclerView.ViewHolder(tagsView) {
    fun set(item: PostAdapter.Item.TagsItem) {
        tagsView.updateTags(item.tags, item.votes)
    }
}

private class InfoLineViewHolder(
        val onDetailClickedListener: PostActions,
        val infoView: InfoLineView) : RecyclerView.ViewHolder(infoView) {

    fun set(item: PostAdapter.Item.InfoItem) {

        // display the feed item in the view
        infoView.setFeedItem(item.item, item.isOurPost, item.vote)
        infoView.onDetailClickedListener = onDetailClickedListener
    }
}

private class CommentPostLineHolder(
        val onDetailClickedListener: PostActions,
        val line: CommentPostLine) : RecyclerView.ViewHolder(line) {

    var latestText: String? = null

    init {
        line.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        line.textChanges().subscribe { text -> latestText = text }
        line.comments().subscribe { text ->
            if (onDetailClickedListener.writeCommentClicked(text)) {
                line.clear()
            }
        }
    }

    fun set(item: PostAdapter.Item.CommentInputItem) {
        line.setCommentDraft(latestText ?: item.text)
    }
}

@SuppressLint("ViewConstructor")
class PlaceholderView(context: Context, var viewer: View? = null) : FrameLayout(context) {
    var fixedHeight = AndroidUtility.dp(context, 150)

    init {
        val v = View(context)
        v.setBackgroundResource(R.drawable.dropshadow_reverse)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtility.dp(context, 8))
        lp.gravity = Gravity.BOTTOM
        v.layoutParams = lp

        addView(v)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, fixedHeight)

        measureChildren(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return viewer?.onTouchEvent(event) ?: false
    }
}

class StaticViewHolder(parent: ViewGroup, @LayoutRes layout: Int)
    : RecyclerView.ViewHolder(parent.layoutInflater.inflate(layout, parent, false))
