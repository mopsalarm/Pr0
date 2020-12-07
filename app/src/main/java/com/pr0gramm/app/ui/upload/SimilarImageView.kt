package com.pr0gramm.app.ui.upload

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.asThumbnail
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.views.InjectorViewMixin
import com.pr0gramm.app.ui.views.SimpleAdapter
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.ui.views.recyclerViewAdapter
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
                    val imageUri = UriHelper.of(context).thumbnail(item.asThumbnail())
                    picasso.load(imageUri)
                            .config(Bitmap.Config.RGB_565)
                            .placeholder(ColorDrawable(0xff333333.toInt()))
                            .into(imageView)

                    imageView.setOnClickListener {
                        handleItemClicked(item)
                    }
                }
            }
        }
    }

    private fun handleItemClicked(item: Api.Posted.SimilarItem) {
        context.startActivity(MainActivity.openItemIntent(context, item.id).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
