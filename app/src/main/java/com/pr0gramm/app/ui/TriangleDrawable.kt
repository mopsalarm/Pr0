package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import androidx.annotation.ColorInt
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.util.getColorCompat

/**
 * A simple triangle drawable
 */
class TriangleDrawable(context: Context, contentType: ContentType, private val size: Int) :
    BaseDrawable(PixelFormat.TRANSPARENT) {
    private val paint: Paint = paint {
        color = colorForContentType(context, contentType)
        style = Paint.Style.FILL
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

    companion object {
        /**
         * Gets a color for the given content type
         */
        @ColorInt
        private fun colorForContentType(context: Context, contentType: ContentType): Int {
            return when (contentType) {
                ContentType.SFW -> context.getColorCompat(R.color.type_sfw)
                ContentType.NSFW -> context.getColorCompat(R.color.type_nsfw)
                ContentType.NSFL -> context.getColorCompat(R.color.type_nsfl)
                ContentType.NSFP -> context.getColorCompat(R.color.type_nsfp)
                ContentType.POL -> context.getColorCompat(R.color.type_pol)
            }
        }
    }
}
