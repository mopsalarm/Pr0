package com.pr0gramm.app.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import android.support.annotation.ColorInt
import com.pr0gramm.app.feed.ContentType

/**
 * A simple triangle drawable
 */
class TriangleDrawable(contentType: ContentType, private val size: Int) : Drawable() {
    private val paint: Paint = Paint().apply {
        color = colorForContentType(contentType)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds

        val path = Path()
        path.moveTo(bounds.left.toFloat(), bounds.bottom.toFloat())
        path.lineTo(bounds.left.toFloat(), (bounds.bottom - size).toFloat())
        path.lineTo((bounds.left + size).toFloat(), bounds.bottom.toFloat())
        path.lineTo(bounds.left.toFloat(), bounds.bottom.toFloat())

        canvas.drawPath(path, paint)
    }

    override fun getIntrinsicWidth(): Int {
        return size
    }

    override fun getIntrinsicHeight(): Int {
        return size
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    companion object {
        /**
         * Gets a color for the given content type
         */
        @ColorInt
        private fun colorForContentType(contentType: ContentType): Int {
            when (contentType) {
                ContentType.SFW -> return Color.parseColor("#a7d713")
                ContentType.NSFW -> return Color.parseColor("#f6ab09")
                ContentType.NSFL -> return Color.parseColor("#d9534f")
                ContentType.NSFP -> return Color.parseColor("#69ccca")
                else -> return Color.GRAY
            }
        }
    }
}
