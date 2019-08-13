package com.pr0gramm.app.ui

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import com.pr0gramm.app.services.Track.context
import com.pr0gramm.app.util.getOrPut

class DrawableCache {
    private val drawableCache = LruCache<CacheKey, Drawable>(32)

    fun get(drawableId: Int, @ColorInt tint: Int): Drawable {
        return drawableCache.getOrPut(CacheKey(drawableId, tint)) {
            context.getDrawable(drawableId)!!.apply {
                mutate()
                setTint(tint)
            }
        }
    }

    private data class CacheKey(val drawableId: Int, val color: Int)
}