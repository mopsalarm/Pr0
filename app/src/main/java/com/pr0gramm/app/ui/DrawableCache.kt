package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import com.pr0gramm.app.ApplicationClass
import com.pr0gramm.app.util.checkMainThread
import com.pr0gramm.app.util.getOrPut

class DrawableCache {
    fun get(drawableId: Int, @ColorInt tint: Int): Drawable {
        return get(ApplicationClass.appContext, drawableId, tint)
    }

    fun get(context: Context, drawableId: Int, @ColorInt tint: Int): Drawable {
        checkMainThread()

        return drawableCache.getOrPut(CacheKey(drawableId, tint)) {
            context.applicationContext.getDrawable(drawableId)!!.apply {
                mutate()
                setTint(tint)
            }
        }
    }

    private data class CacheKey(val drawableId: Int, val color: Int)

    companion object {
        // we are using a shared lru cache
        private val drawableCache = LruCache<CacheKey, Drawable>(64)
    }
}


