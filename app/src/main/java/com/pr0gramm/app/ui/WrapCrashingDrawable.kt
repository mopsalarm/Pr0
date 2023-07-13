package com.pr0gramm.app.ui

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.appcompat.graphics.drawable.DrawableWrapperCompat

/**
 * There is a bug on some Samsung devices. Those devices are crashing if they are
 * painting a round gradient drawable. Say whaaat!? Okay, we just catch a crash
 * like that and paint again with the fallback color.
 */
class WrapCrashingDrawable(@ColorInt private val fallbackColor: Int, drawable: Drawable) :
    DrawableWrapperCompat(drawable) {
    override fun draw(canvas: Canvas) {
        try {
            super.draw(canvas)
        } catch (ignored: Exception) {
            canvas.drawColor(fallbackColor)
        }
    }
}
