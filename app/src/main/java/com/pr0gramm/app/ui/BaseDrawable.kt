package com.pr0gramm.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable

abstract class BaseDrawable(private val opacity: Int) : Drawable() {
    abstract override fun draw(canvas: Canvas)

    override fun setAlpha(p0: Int) {
    }

    override fun getOpacity(): Int {
        return opacity
    }

    override fun setColorFilter(p0: ColorFilter?) {
    }
}

/**
 * Creates and configures a Paint object. Enables antialiasing by default.
 */
fun paint(configure: Paint.() -> Unit): Paint {
    val p = Paint()
    p.isAntiAlias = true
    p.configure()
    return p
}