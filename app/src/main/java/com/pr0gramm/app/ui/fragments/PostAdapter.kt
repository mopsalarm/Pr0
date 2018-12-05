package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.DelegateAdapter
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.staticLayoutAdapterDelegate
import com.pr0gramm.app.ui.views.CommentPostLine
import com.pr0gramm.app.ui.views.InfoLineView
import com.pr0gramm.app.ui.views.PostActions
import com.pr0gramm.app.ui.views.TagsView
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.removeFromParent
import gnu.trove.map.TLongObjectMap

@Suppress("NOTHING_TO_INLINE")
private inline fun idInCategory(cat: Long, idOffset: Long = 0): Long {
    return (idOffset shl 8) or cat
}

class PostAdapter(commentViewListener: CommentView.Listener, postActions: PostActions)
    : DelegateAdapter<PostAdapter.Item>(ItemCallback(), name = "PostAdapter") {

    init {
        setHasStableIds(true)

        delegates += CommentItemAdapterDelegate(commentViewListener)
        delegates += InfoLineItemAdapterDelegate(postActions)
        delegates += TagsViewHolderAdapterDelegate(postActions)
        delegates += CommentPostLineAdapterDelegate(postActions)
        delegates += PlaceholderItemAdapterDelegate
        delegates += staticLayoutAdapterDelegate(R.layout.comments_are_loading, Item.CommentsLoadingItem)
        delegates += staticLayoutAdapterDelegate(R.layout.comments_load_err, Item.LoadErrorItem)
        delegates += staticLayoutAdapterDelegate(R.layout.comments_item_deleted, Item.PostIsDeletedItem)
        delegates += staticLayoutAdapterDelegate(R.layout.comments_no_account, Item.NoCommentsWithoutAccount)
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    sealed class Item(val id: Long) {
        class PlaceholderItem(val height: Int, val viewer: View, val mediaControlsContainer: View?)
            : Item(idInCategory(0)) {

            override fun hashCode(): Int = height
            override fun equals(other: Any?): Boolean = other is PlaceholderItem && other.height == height
        }

        data class InfoItem(val item: FeedItem, val vote: Vote, val isOurPost: Boolean)
            : Item(idInCategory(1))

        data class TagsItem(val tags: List<Api.Tag>, val votes: TLongObjectMap<Vote>)
            : Item(idInCategory(2))

        data class CommentInputItem(val text: String)
            : Item(idInCategory(3))

        object CommentsLoadingItem
            : Item(idInCategory(4))

        object LoadErrorItem
            : Item(idInCategory(5))

        object PostIsDeletedItem
            : Item(idInCategory(6))

        object NoCommentsWithoutAccount
            : Item(idInCategory(7))

        data class CommentItem(val commentTreeItem: CommentTree.Item)
            : Item(idInCategory(8, commentTreeItem.comment.id))
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

private class CommentItemAdapterDelegate(private val commentActionListener: CommentView.Listener)
    : ListItemTypeAdapterDelegate<PostAdapter.Item.CommentItem, CommentView>() {

    override fun onCreateViewHolder(parent: ViewGroup): CommentView {
        return CommentView(parent, commentActionListener)
    }

    override fun onBindViewHolder(holder: CommentView, value: PostAdapter.Item.CommentItem) {
        holder.set(value.commentTreeItem)
    }
}

private class TagsViewHolderAdapterDelegate(private val postActions: PostActions)
    : ListItemTypeAdapterDelegate<PostAdapter.Item.TagsItem, TagsViewHolderAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(TagsView(parent.context, postActions))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.TagsItem) {
        holder.tagsView.updateTags(value.tags, value.votes)
    }

    private class ViewHolder(val tagsView: TagsView) : RecyclerView.ViewHolder(tagsView)
}


private class InfoLineItemAdapterDelegate(private val postActions: PostActions)
    : ListItemTypeAdapterDelegate<PostAdapter.Item.InfoItem, InfoLineItemAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(InfoLineView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.InfoItem) {
        // display the feed item in the view
        holder.infoView.setFeedItem(value.item, value.isOurPost, value.vote)
        holder.infoView.onDetailClickedListener = postActions
    }

    private class ViewHolder(val infoView: InfoLineView) : RecyclerView.ViewHolder(infoView) {
        init {
            infoView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}


private class CommentPostLineAdapterDelegate(private val postActions: PostActions)
    : ListItemTypeAdapterDelegate<PostAdapter.Item.CommentInputItem, CommentPostLineAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(CommentPostLine(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.CommentInputItem) {
        holder.set(value)
    }

    private inner class ViewHolder(val line: CommentPostLine) : RecyclerView.ViewHolder(line) {
        var latestText: String? = null

        init {
            line.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)

            line.textChanges().subscribe { text -> latestText = text }

            line.comments().subscribe { text ->
                if (postActions.writeCommentClicked(text)) {
                    line.clear()
                }
            }
        }

        fun set(item: PostAdapter.Item.CommentInputItem) {
            line.setCommentDraft(latestText ?: item.text)
        }
    }
}

private object PlaceholderItemAdapterDelegate
    : ListItemTypeAdapterDelegate<PostAdapter.Item.PlaceholderItem, PlaceholderItemAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(PlaceholderView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.PlaceholderItem) {
        holder.set(value)
    }

    private class ViewHolder(val pv: PlaceholderView) : RecyclerView.ViewHolder(pv) {
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
}


@SuppressLint("ViewConstructor")
private class PlaceholderView(context: Context, var viewer: View? = null) : FrameLayout(context) {
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return viewer?.onTouchEvent(event) ?: false
    }
}
