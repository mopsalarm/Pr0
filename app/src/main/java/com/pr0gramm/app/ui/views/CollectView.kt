package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.base.whileIsAttachedScope
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.getStyledColor
import com.pr0gramm.app.util.getStyledResourceId
import kotlin.properties.Delegates

class CollectView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AppCompatImageView(context, attrs, defStyleAttr) {

    private val collectionsService: CollectionsService = context.injector.instance()
    private val drawableCache = DrawableCache()

    var onShowCollectionsClicked: ((itemId: Long) -> Unit)? = null

    var itemId: Long by Delegates.observable(initialValue = 0L) { _, _, newValue -> onUpdateItemId(newValue) }

    init {
        setImageDrawable(R.drawable.ic_collection_no,
                context.getStyledResourceId(android.R.attr.textColorSecondary))

        setOnClickListener { onShowCollectionsClicked?.invoke(itemId) }
    }


    private fun onUpdateItemId(itemId: Long) {
        whileIsAttachedScope {
            if (collectionsService.isItemInAnyCollection(itemId)) {
                setImageDrawable(R.drawable.ic_collection_yes, ThemeHelper.accentColor)
            } else {
                setImageDrawable(R.drawable.ic_collection_no,
                        context.getStyledResourceId(android.R.attr.textColorSecondary))
            }
        }
    }

    private fun setImageDrawable(@DrawableRes drawableId: Int, @ColorRes color: Int) {
        setImageDrawable(drawableCache.get(context, drawableId, context.getColorCompat(color)))
    }
}