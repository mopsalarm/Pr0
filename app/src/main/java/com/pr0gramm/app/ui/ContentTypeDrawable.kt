package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.save
import java.util.Locale

/**
 */
class ContentTypeDrawable(context: Context, types: Collection<ContentType>) : Drawable() {
    private val text: String

    var textSize: Float by observeChange(0.toFloat()) { invalidateSelf() }

    init {
        textSize = 16f

        if (ContentType.values().all { it in types }) {
            text = context.getString(R.string.all)
        } else {
            text = types
                    .filter { it !== ContentType.NSFP }
                    .joinToString("\n") { it.name.lowercase(Locale.ROOT) }
        }
    }

    override fun draw(canvas: Canvas) {
        val tp = TextPaint()
        tp.color = Color.WHITE
        tp.textSize = textSize
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.isAntiAlias = true

        val layout = StaticLayout(text, tp, bounds.width(),
                Layout.Alignment.ALIGN_CENTER, 0.8f, 0f, false)

        canvas.save {
            // center the text on the icon
            val rect = bounds
            canvas.translate(
                    0.5f * (rect.width() - layout.width),
                    0.5f * (rect.height() - layout.height))

            layout.draw(canvas)
        }
    }

    override fun getIntrinsicWidth(): Int {
        return 96
    }

    override fun getIntrinsicHeight(): Int {
        return 96
    }

    override fun setAlpha(alpha: Int) {
        // do nothing
    }

    override fun setColorFilter(cf: ColorFilter?) {
        // yea, just do nothing..
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
