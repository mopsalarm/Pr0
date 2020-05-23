package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.getStyledResourceId
import kotlinx.coroutines.flow.collect
import kotlin.properties.Delegates

class CollectView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AppCompatImageView(context, attrs, defStyleAttr) {

    private val collectionItemsService: CollectionItemsService = context.injector.instance()

    var itemId: Long by Delegates.observable(initialValue = 0L) { _, _, newValue -> onUpdateItemId() }

    init {
        setImageDrawable(R.drawable.ic_collection_no,
                context.getStyledResourceId(android.R.attr.textColorSecondary))

        onAttachedScope {
            collectionItemsService.updateTime.collect {
                onUpdateItemId()
            }
        }
    }

    private fun onUpdateItemId() {
        if (collectionItemsService.isItemInAnyCollection(itemId)) {
            setImageDrawable(R.drawable.ic_collection_yes, ThemeHelper.accentColor)
        } else {
            setImageDrawable(R.drawable.ic_collection_no,
                    context.getStyledResourceId(android.R.attr.textColorSecondary))
        }
    }

    private fun setImageDrawable(@DrawableRes drawableId: Int, @ColorRes color: Int) {
        setImageDrawable(DrawableCache.get(context, drawableId, context.getColorCompat(color)))
    }
}