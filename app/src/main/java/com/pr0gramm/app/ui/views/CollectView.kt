package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageButton
import com.pr0gramm.app.R
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.getStyledColor
import com.pr0gramm.app.util.observeChange

class CollectView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AppCompatImageButton(context, attrs, defStyleAttr) {

    private val collectionItemsService: CollectionItemsService = context.injector.instance()

    var itemId: Long by observeChange(def = 0L) { onUpdateItemId() }

    val isCollected: Boolean
        get() = collectionItemsService.isItemInAnyCollection(itemId)

    init {
        setImageDrawable(R.drawable.ic_collection_no,
                context.getStyledColor(android.R.attr.textColorSecondary))

        onAttachedScope {
            collectionItemsService.updateTime.collect {
                onUpdateItemId()
            }
        }
    }

    private fun onUpdateItemId() {
        if (isCollected) {
            setImageDrawable(R.drawable.ic_collection_yes,
                    context.getColorCompat(ThemeHelper.accentColor))
        } else {
            setImageDrawable(R.drawable.ic_collection_no,
                    context.getStyledColor(android.R.attr.textColorSecondary))
        }
    }

    private fun setImageDrawable(@DrawableRes drawableId: Int, @ColorInt color: Int) {
        setImageDrawable(DrawableCache.get(context, drawableId, color))
    }
}