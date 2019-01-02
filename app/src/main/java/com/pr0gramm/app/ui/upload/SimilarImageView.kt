package com.pr0gramm.app.ui.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.dialogs.PopupPlayer
import com.pr0gramm.app.ui.views.InjectorViewMixin
import com.pr0gramm.app.ui.views.SimpleAdapter
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.ui.views.recyclerViewAdapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.observeChange
import com.squareup.picasso.Picasso

/**
 */
class SimilarImageView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr), InjectorViewMixin {

    private val picasso: Picasso by instance()

    init {
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
    }

    var items: List<Api.Posted.SimilarItem> by observeChange(listOf()) {
        adapter = itemAdapter(items)
    }

    private fun itemAdapter(items: List<Api.Posted.SimilarItem>): SimpleAdapter<Api.Posted.SimilarItem> {
        return recyclerViewAdapter(items) {
            handle<Api.Posted.SimilarItem>() with layout(R.layout.thumbnail) {
                val imageView = it as ImageView

                bind { item ->
                    val imageUri = UriHelper.of(context).thumbnail(item)
                    picasso.load(imageUri)
                            .config(Bitmap.Config.RGB_565)
                            .placeholder(ColorDrawable(0xff333333.toInt()))
                            .into(imageView)

                    imageView.setOnClickListener { _ ->
                        handleItemClicked(item)
                    }
                }
            }
        }
    }

    private fun handleItemClicked(item: Api.Posted.SimilarItem) {
        val activity = AndroidUtility.activityFromContext(context) as? FragmentActivity ?: return

        val fakeItem = FeedItem(Api.Feed.Item(
                id = item.id, image = item.image, thumb = item.thumbnail,
                promoted = 0L, audio = false, created = Instant.now(),
                up = 0, down = 0, fullsize = "", width = 0, height = 0, user = "",
                mark = 0, deleted = false, flags = 0))

        PopupPlayer.open(activity, fakeItem)
    }
}
