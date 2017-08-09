package com.pr0gramm.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable

abstract class BaseDrawable(private val opacity: Int) : Drawable() {
    override abstract fun draw(canvas: Canvas)

    override fun setAlpha(p0: Int) {
    }

    override fun getOpacity(): Int {
        return opacity
    }

    override fun setColorFilter(p0: ColorFilter?) {
    }
}