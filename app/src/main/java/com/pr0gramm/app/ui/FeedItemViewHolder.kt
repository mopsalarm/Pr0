package com.pr0gramm.app.ui

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.visible

/**
 * View holder for one feed item.
 */
class FeedItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val seen: ImageView = find(R.id.seen)
    private val repost: ImageView = find(R.id.repost)
    private val preloaded: View = find(R.id.preloaded)

    val image: ImageView = find(R.id.image)

    var item: FeedItem? = null
    var index: Int = 0

    fun setIsRepost() {
        repost.visible = true
        seen.visible = false
    }

    fun setIsSeen() {
        seen.visible = true
        repost.visible = false
    }

    fun setIsPreloaded(isPreloaded: Boolean) {
        preloaded.visible = isPreloaded
    }

    fun clear() {
        seen.visible = false
        repost.visible = false
    }
}
