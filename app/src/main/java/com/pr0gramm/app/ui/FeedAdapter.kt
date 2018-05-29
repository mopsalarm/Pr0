package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.support.annotation.LayoutRes
import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.services.UserInfo
import com.pr0gramm.app.ui.fragments.AdViewHolder
import com.pr0gramm.app.ui.views.OnUserClickedListener
import com.pr0gramm.app.ui.views.UserHintView
import com.pr0gramm.app.ui.views.UserInfoView
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.inflate
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.visible
import com.squareup.picasso.Picasso

private enum class Offset(val offset: Long, val type: Class<out FeedAdapter.Entry>) {
    Ad(100, FeedAdapter.Entry.Ad::class.java),
    Hint(200, FeedAdapter.Entry.UserHint::class.java),
    Spacer(300, FeedAdapter.Entry.Spacer::class.java),
    User(400, FeedAdapter.Entry.User::class.java),
    Item(1000, FeedAdapter.Entry.Item::class.java),
    Comments(1_000_000_000, FeedAdapter.Entry.Comment::class.java)
}

class FeedAdapter(private val picasso: Picasso,
                  private val userHintClickedListener: OnUserClickedListener,
                  private val userActionListener: UserInfoView.UserActionListener)
    : AsyncListAdapter<FeedAdapter.Entry, RecyclerView.ViewHolder>(ItemCallback()) {

    init {
        setHasStableIds(true)
    }

    var latestEntries: List<Entry> = listOf()
        private set

    private val viewTypesByType = Offset.values().associateBy { it.type }
    private val viewTypesByIndex = Offset.values()

    override fun submitList(newList: List<Entry>) {
        super.submitList(newList)
        latestEntries = newList
    }

    override fun getItemViewType(position: Int): Int {
        val type = getItem(position).javaClass
        return viewTypesByType.getValue(type).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewTypesByIndex[viewType]) {
            Offset.Item -> {
                FeedItemViewHolder(parent.layoutInflater.inflate(R.layout.feed_item_view))
            }

            Offset.Comments -> {
                val view = parent.layoutInflater.inflate(R.layout.user_info_comment) as MessageView
                return CommentViewHolder(view)
            }

            Offset.Spacer -> SpacerViewHolder(parent.context)

            Offset.Ad -> AdViewHolder.new(parent.context)

            Offset.Hint -> {
                UserHintViewHolder(UserHintView(parent.context))
            }

            Offset.User -> {
                UserInfoViewHolder(UserInfoView(parent.context, userActionListener))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = getItem(position)

        when (holder) {
            is FeedItemViewHolder ->
                holder.bindTo(picasso, entry as Entry.Item)

            is CommentViewHolder ->
                holder.bindTo(entry as Entry.Comment)

            is UserHintViewHolder ->
                holder.bindTo(entry as Entry.UserHint, userHintClickedListener)

            is SpacerViewHolder ->
                holder.bindTo(entry as Entry.Spacer)

            is UserInfoViewHolder ->
                holder.bindTo(entry as Entry.User)
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

        data class UserHint(val user: Api.Info.User) : Entry(Offset.Hint.offset)

        data class Spacer(private val idx: Int,
                          val height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
                          @LayoutRes val layout: Int? = null) : Entry(Offset.Spacer.offset + idx)

        data class Comment(val message: Api.Message, val currentUsername: String?) : Entry(Offset.Comments.offset + message.id())

        data class User(val user: UserInfo, val myself: Boolean) : Entry(Offset.User.offset)
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

/**
 * View holder for one feed item.
 */
class FeedItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val seen: ImageView = find(R.id.seen)
    private val repost: ImageView = find(R.id.repost)
    private val preloaded: View = find(R.id.preloaded)

    val image: ImageView = find(R.id.image)

    lateinit var item: FeedItem
        private set

    private fun setIsRepost() {
        repost.visible = true
        seen.visible = false
    }

    private fun setIsSeen() {
        seen.visible = true
        repost.visible = false
    }

    private fun clear() {
        seen.visible = false
        repost.visible = false
    }

    fun bindTo(picasso: Picasso, entry: FeedAdapter.Entry.Item) {
        val item = entry.item

        val imageUri = UriHelper.of(itemView.context).thumbnail(item)
        picasso.load(imageUri)
                .config(Bitmap.Config.RGB_565)
                .placeholder(ColorDrawable(0xff333333.toInt()))
                .into(image)

        this.itemView.tag = this
        this.item = item

        // show preload-badge
        preloaded.visible = entry.preloaded

        when {
            entry.repost -> setIsRepost()
            entry.seen -> setIsSeen()
            else -> clear()
        }
    }
}

private class UserHintViewHolder(private val hintView: UserHintView)
    : RecyclerView.ViewHolder(hintView) {

    fun bindTo(entry: FeedAdapter.Entry.UserHint, onClick: OnUserClickedListener) {
        hintView.update(entry.user, onClick)
    }
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

private class CommentViewHolder(view: MessageView) : MessageAdapter.MessageViewHolder(view) {
    fun bindTo(entry: FeedAdapter.Entry.Comment) {
        val message = entry.message

        bindTo(message, null, entry.currentUsername)

        itemView.setOnClickListener {
            val context = itemView.context

            // open the post in "new"
            val uri = UriHelper.of(context).post(FeedType.NEW, message.itemId(), message.id())
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
