package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
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
import java.util.*

private enum class Offset(val offset: Long, val type: Class<out FeedAdapter.Entry>) {
    UserHint(200, FeedAdapter.Entry.UserHint::class.java),
    UserInfoLoading(201, FeedAdapter.Entry.UserLoading::class.java),
    UserInfo(202, FeedAdapter.Entry.User::class.java),
    Error(203, FeedAdapter.Entry.Error::class.java),
    EmptyHint(204, FeedAdapter.Entry.EmptyHint::class.java),
    Spacer(300, FeedAdapter.Entry.Spacer::class.java),
    Item(1000, FeedAdapter.Entry.Item::class.java),
    Ad(900_000_000, FeedAdapter.Entry.Ad::class.java),
    Comments(1_000_000_000, FeedAdapter.Entry.Comment::class.java)
}

class FeedAdapter(private val picasso: Picasso,
                  private val userHintClickedListener: OnUserClickedListener,
                  private val userActionListener: UserInfoView.UserActionListener)

    : AsyncListAdapter<FeedAdapter.Entry, androidx.recyclerview.widget.RecyclerView.ViewHolder>(ItemCallback(), name = "FeedAdapter") {

    private var lastSeenAdview: AdView? = null

    init {
        setHasStableIds(true)

        if (Offset.values().size != Offset.values().map { it.offset }.distinct().size)
            throw IllegalArgumentException("Error in Offset() mapping")

        if (Offset.values().size != Offset.values().map { it.type }.distinct().size)
            throw IllegalArgumentException("Error in Offset() mapping")
    }

    /**
     * The list of entries that is currently displayed.
     */
    @Volatile
    var latestEntries: List<Entry> = listOf()
        private set

    private val viewTypesByType = IdentityHashMap(Offset.values().associateBy { it.type })
    private val viewTypesByIndex = Offset.values().toList()

    fun destroyAdView() {
        lastSeenAdview?.removeFromParent()
        lastSeenAdview?.destroy()
        lastSeenAdview = null
    }

    override fun submitList(newList: List<Entry>, forceSync: Boolean) {
        latestEntries = newList
        super.submitList(newList, forceSync)
    }

    override fun getItemViewType(position: Int): Int {
        val type = getItem(position).javaClass
        return viewTypesByType.getValue(type).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        trace { "onCreateViewHolder($viewType)" }

        val context = parent.context
        
        return when (viewTypesByIndex[viewType]) {
            Offset.Item -> {
                FeedItemViewHolder(parent.layoutInflater.inflate(R.layout.feed_item_view) as FrameLayout)
            }

            Offset.Comments -> {
                val view = parent.layoutInflater.inflate(R.layout.user_info_comment) as MessageView
                CommentViewHolder(view)
            }

            Offset.Spacer -> SpacerViewHolder(context)

            Offset.Ad -> {
                val view = AdViewHolder.new(context)
                lastSeenAdview = view.adView
                view
            }

            Offset.UserHint -> {
                UserHintViewHolder(UserHintView(context))
            }

            Offset.UserInfoLoading -> {
                UserInfoLoadingViewHolder(UserInfoLoadingView(context))
            }

            Offset.UserInfo -> {
                UserInfoViewHolder(UserInfoView(context, userActionListener))
            }

            Offset.Error -> {
                val view = parent.layoutInflater.inflate(R.layout.feed_error) as ViewGroup
                ErrorViewHolder(view)
            }

            Offset.EmptyHint -> {
                val view = parent.layoutInflater.inflate(R.layout.feed_hint_empty)
                NoopViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        ignoreException {
            val entry = getItem(position)
            trace { "onBindViewHolder($position, item=${entry.javaClass.simpleName})" }

            when (holder) {
                is FeedItemViewHolder ->
                    holder.bindTo(picasso, entry as Entry.Item)

                is CommentViewHolder ->
                    holder.bindTo(entry as Entry.Comment)

                is UserHintViewHolder ->
                    holder.bindTo(entry as Entry.UserHint, userHintClickedListener)

                is UserInfoLoadingViewHolder ->
                    holder.bindTo(entry as Entry.UserLoading)

                is SpacerViewHolder ->
                    holder.bindTo(entry as Entry.Spacer)

                is UserInfoViewHolder ->
                    holder.bindTo(entry as Entry.User)

                is ErrorViewHolder ->
                    holder.bindTo(entry as Entry.Error)
            }
        }
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
        data class Item(val item: FeedItem,
                        val repost: Boolean = false,
                        val preloaded: Boolean = false,
                        val seen: Boolean = false) : Entry(Offset.Item.offset + item.id)

        data class Ad(val index: Long = 0) : Entry(Offset.Ad.offset + index)

        data class UserHint(val user: UserAndMark) : Entry(Offset.UserHint.offset)

        data class UserLoading(val user: UserAndMark) : Entry(Offset.UserInfoLoading.offset)

        data class Spacer(private val idx: Int,
                          val height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                          @LayoutRes val layout: Int? = null) : Entry(Offset.Spacer.offset + idx)

        data class Comment(val message: Api.Message, val currentUsername: String?) : Entry(Offset.Comments.offset + message.id)

        data class User(val user: UserInfo, val myself: Boolean) : Entry(Offset.UserInfo.offset)

        data class Error(val message: String) : Entry(Offset.Error.offset)

        object EmptyHint : Entry(Offset.EmptyHint.offset)
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
        return flagView ?: (itemView.layoutInflater
                .inflate(R.layout.feed_item_view_flag, container, false) as ImageView)
                .also { view ->
                    flagView = view
                    container.addView(view)
                }
    }

    private fun ensureOverlayView(): ImageView {
        return overlayView ?: (itemView.layoutInflater
                .inflate(R.layout.feed_item_view_overlay, container, false) as ImageView)
                .also { view ->
                    overlayView = view

                    // add view directly above the image view
                    val idx = container.indexOfChild(imageView) + 1
                    container.addView(view, idx)
                }
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

        val imageUri = UriHelper.of(itemView.context).thumbnail(item)
        picasso.load(imageUri)
                .config(Bitmap.Config.RGB_565)
                .placeholder(ColorDrawable(0xff333333.toInt()))
                .into(imageView)

        this.itemView.tag = this
        this.item = item

        when {
            entry.repost -> setItemOverlay(R.drawable.ic_repost)
            entry.seen -> setItemOverlay(R.drawable.ic_checked)
            else -> overlayView?.visible = false
        }

        when {
            entry.item.isPinned -> setItemFlag(R.drawable.feed_pinned)
            entry.preloaded -> setItemFlag(R.drawable.feed_offline)
            else -> flagView?.visible = false
        }
    }
}

private class UserHintViewHolder(private val hintView: UserHintView)
    : RecyclerView.ViewHolder(hintView) {

    fun bindTo(entry: FeedAdapter.Entry.UserHint, onClick: OnUserClickedListener) {
        hintView.update(entry.user.name, entry.user.mark, onClick)
    }
}

private class UserInfoLoadingViewHolder(private val hintView: UserInfoLoadingView)
    : RecyclerView.ViewHolder(hintView) {

    fun bindTo(entry: FeedAdapter.Entry.UserLoading) {
        hintView.update(entry.user.name, entry.user.mark)
    }
}

private class ErrorViewHolder(errorView: ViewGroup)
    : RecyclerView.ViewHolder(errorView) {

    val textView = errorView.find<TextView>(R.id.error)

    fun bindTo(entry: FeedAdapter.Entry.Error) {
        textView.text = entry.message
    }
}

private class NoopViewHolder(view: View) : RecyclerView.ViewHolder(view)

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

private class UserInfoViewHolder(private val view: UserInfoView) : RecyclerView.ViewHolder(view) {
    fun bindTo(entry: FeedAdapter.Entry.User) {
        view.updateUserInfo(entry.user.info, entry.user.comments, entry.myself)
    }
}
