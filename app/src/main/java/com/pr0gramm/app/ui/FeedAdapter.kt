package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.asThumbnail
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.services.UserInfo
import com.pr0gramm.app.ui.fragments.AdViewHolder
import com.pr0gramm.app.ui.views.OnUserClickedListener
import com.pr0gramm.app.ui.views.UserHintView
import com.pr0gramm.app.ui.views.UserInfoLoadingView
import com.pr0gramm.app.ui.views.UserInfoView
import com.pr0gramm.app.util.*
import com.squareup.picasso.Picasso

private enum class Offset(val offset: Long) {
    UserHint(200),
    UserInfoLoading(201),
    UserInfo(202),
    Error(203),
    EmptyHint(204),
    LoadingHint(205),
    Spacer(300),
    Item(1000),
    Ad(900_000_000),
    Comments(1_000_000_000)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun idInCategory(cat: Long, idOffset: Long = 0): Long {
    return (idOffset shl 8) or cat
}

class FeedAdapter(picasso: Picasso,
                  userHintClickedListener: OnUserClickedListener,
                  userActionListener: UserInfoView.UserActionListener)

    : DelegateAdapter<FeedAdapter.Entry>(ItemCallback(), name = "FeedAdapter") {

    private val adAdapter = AdViewAdapter()

    init {
        setHasStableIds(true)

        delegates += FeedItemEntryAdapter(picasso)
        delegates += CommentEntryAdapter
        delegates += adAdapter
        delegates += UserEntryAdapter(userActionListener)
        delegates += UserHintEntryAdapter(userHintClickedListener)
        delegates += UserLoadingEntryAdapter
        delegates += SpacerEntryAdapter
        delegates += ErrorAdapterDelegate(R.layout.feed_error)
        delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_empty, Entry.EmptyHint)
        delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_loading, Entry.LoadingHint)
    }

    /**
     * The list of entries that is currently displayed.
     */
    @Volatile
    var latestEntries: List<Entry> = listOf()
        private set

    fun destroyAdView() {
        adAdapter.destroy()
    }

    override fun submitList(newList: List<Entry>, forceSync: Boolean) {
        latestEntries = newList
        super.submitList(newList, forceSync)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    class ItemCallback : DiffUtil.ItemCallback<FeedAdapter.Entry>() {
        override fun areItemsTheSame(oldItem: FeedAdapter.Entry, newItem: FeedAdapter.Entry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FeedAdapter.Entry, newItem: FeedAdapter.Entry): Boolean {
            return oldItem == newItem
        }
    }

    sealed class Entry(val id: Long) {
        data class UserHint(val user: UserAndMark)
            : Entry(idInCategory(0))

        data class UserLoading(val user: UserAndMark)
            : Entry(idInCategory(1))

        data class User(val user: UserInfo, val myself: Boolean)
            : Entry(idInCategory(2))

        data class Error(override val errorText: String)
            : Entry(idInCategory(3)), ErrorAdapterDelegate.Value

        object EmptyHint
            : Entry(idInCategory(4))

        object LoadingHint
            : Entry(idInCategory(5))

        data class Item(val item: FeedItem, val repost: Boolean = false, val preloaded: Boolean, val seen: Boolean)
            : Entry(idInCategory(6, item.id))

        data class Spacer(val idx: Long, val height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, @LayoutRes val layout: Int? = null)
            : Entry(idInCategory(7, idx))

        data class Ad(val index: Long)
            : Entry(idInCategory(8, index))

        data class Comment(val message: Api.Message, val currentUsername: String?)
            : Entry(idInCategory(9, message.id))
    }

    inner class SpanSizeLookup(private val spanCount: Int) : GridLayoutManager.SpanSizeLookup() {
        init {
            isSpanIndexCacheEnabled = true
        }

        override fun getSpanSize(position: Int): Int {
            return if (getItem(position) is Entry.Item) 1 else spanCount
        }
    }
}

data class UserAndMark(val name: String, val mark: Int)


private class AdViewAdapter
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.Ad, AdViewHolder>() {

    private var lastSeenAdview: AdView? = null

    override fun onCreateViewHolder(parent: ViewGroup): AdViewHolder {
        val view = AdViewHolder.new(parent.context)
        lastSeenAdview = view.adView
        return view
    }

    override fun onBindViewHolder(holder: AdViewHolder, value: FeedAdapter.Entry.Ad) {
    }

    fun destroy() {
        lastSeenAdview?.removeFromParent()
        lastSeenAdview?.destroy()
        lastSeenAdview = null
    }
}

private class FeedItemEntryAdapter(private val picasso: Picasso)
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.Item, FeedItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): FeedItemViewHolder {
        return FeedItemViewHolder(parent.layoutInflater.inflate(R.layout.feed_item_view) as FrameLayout)
    }

    override fun onBindViewHolder(holder: FeedItemViewHolder, value: FeedAdapter.Entry.Item) {
        holder.bindTo(picasso, value)
    }
}

/**
 * View holder for one feed item.
 */
class FeedItemViewHolder(private val container: FrameLayout) : RecyclerView.ViewHolder(container) {
    val imageView: ImageView = find(R.id.image)

    // lazy views
    private var flagView: ImageView? = null
    private var overlayView: ImageView? = null

    lateinit var item: FeedItem
        private set

    private fun ensureFlagView(): ImageView {
        return flagView ?: inflateView(R.layout.feed_item_view_flag).also { view ->
            flagView = view
            container.addView(view)
        }
    }

    private fun ensureOverlayView(): ImageView {
        return overlayView ?: inflateView(R.layout.feed_item_view_overlay).also { view ->
            overlayView = view

            // add view directly above the image view
            val idx = container.indexOfChild(imageView) + 1
            container.addView(view, idx)
        }
    }

    private fun inflateView(id: Int): ImageView {
        return itemView.layoutInflater.inflate(id, container, false) as ImageView
    }

    private fun setItemFlag(@DrawableRes res: Int) {
        val view = ensureFlagView()
        view.setImageResource(res)
        view.visible = true
    }

    private fun setItemOverlay(@DrawableRes res: Int) {
        val view = ensureOverlayView()
        view.setImageResource(res)
        view.visible = true
    }

    fun bindTo(picasso: Picasso, entry: FeedAdapter.Entry.Item) {
        val item = entry.item

        val imageUri = UriHelper.of(itemView.context).thumbnail(item.asThumbnail())
        picasso.load(imageUri)
                .config(Bitmap.Config.RGB_565)
                .placeholder(ColorDrawable(0xff333333.toInt()))
                .into(imageView)

        this.itemView.tag = this
        this.item = item

        when {
            entry.repost -> setItemOverlay(R.drawable.ic_repost)
            entry.seen -> setItemOverlay(R.drawable.ic_check)
            else -> overlayView?.visible = false
        }

        when {
            entry.item.isPinned -> setItemFlag(R.drawable.feed_pinned)
            entry.preloaded -> setItemFlag(R.drawable.feed_offline)
            else -> flagView?.visible = false
        }
    }
}

private class UserHintEntryAdapter(private val onClick: OnUserClickedListener)
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.UserHint, UserHintEntryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(UserHintView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: FeedAdapter.Entry.UserHint) {
        holder.hintView.update(value.user.name, value.user.mark, onClick)
    }

    private class ViewHolder(val hintView: UserHintView) : RecyclerView.ViewHolder(hintView)
}

private object UserLoadingEntryAdapter
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.UserLoading, UserLoadingEntryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(UserInfoLoadingView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: FeedAdapter.Entry.UserLoading) {
        holder.hintView.update(value.user.name, value.user.mark)
    }

    private class ViewHolder(val hintView: UserInfoLoadingView) : RecyclerView.ViewHolder(hintView)
}


private object SpacerEntryAdapter
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.Spacer, SpacerEntryAdapter.SpacerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): SpacerViewHolder {
        return SpacerViewHolder(parent.context)
    }

    override fun onBindViewHolder(holder: SpacerViewHolder, value: FeedAdapter.Entry.Spacer) {
        holder.bindTo(value)
    }

    private class SpacerViewHolder(context: Context) : RecyclerView.ViewHolder(FrameLayout(context)) {
        private val view = itemView as FrameLayout

        @LayoutRes
        private var layoutId: Int? = null

        fun bindTo(spacer: FeedAdapter.Entry.Spacer) {
            itemView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    spacer.height)

            if (spacer.layout != null && layoutId != spacer.layout) {
                view.removeAllViews()
                view.layoutInflater.inflate(spacer.layout, view, true)
                layoutId = spacer.layout
            }
        }
    }
}

private object CommentEntryAdapter
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.Comment, CommentEntryAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): CommentViewHolder {
        val inflater = parent.layoutInflater
        return CommentViewHolder(inflater.inflate(R.layout.user_info_comment) as MessageView)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, value: FeedAdapter.Entry.Comment) {
        holder.bindTo(value)
    }

    private class CommentViewHolder(view: MessageView) : MessageAdapter.MessageViewHolder(view) {
        fun bindTo(entry: FeedAdapter.Entry.Comment) {
            val message = entry.message

            bindTo(message, null, entry.currentUsername)

            itemView.setOnClickListener {
                val context = itemView.context

                // open the post in "new"
                val uri = UriHelper.of(context).post(FeedType.NEW, message.itemId, message.id)
                val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
                context.startActivity(intent)
            }
        }
    }
}

private class UserEntryAdapter(private val userActionListener: UserInfoView.UserActionListener)
    : ListItemTypeAdapterDelegate<FeedAdapter.Entry.User, UserEntryAdapter.UserInfoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): UserInfoViewHolder {
        return UserInfoViewHolder(UserInfoView(parent.context, userActionListener))
    }

    override fun onBindViewHolder(holder: UserInfoViewHolder, value: FeedAdapter.Entry.User) {
        holder.view.updateUserInfo(value.user.info, value.user.comments, value.myself)
    }

    private class UserInfoViewHolder(val view: UserInfoView) : RecyclerView.ViewHolder(view)
}

