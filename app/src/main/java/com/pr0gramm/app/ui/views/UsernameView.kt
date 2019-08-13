package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toRectF
import androidx.core.text.inSpans
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.BaseDrawable
import com.pr0gramm.app.ui.paint
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.getColorCompat
import rx.Observable


/**
 */
class UsernameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {

    private val userClassesService: UserClassesService = if (isInEditMode) {
        UserClassesService(Observable.empty())
    } else {
        context.injector.instance()
    }

    init {
        maxLines = 1

        if (isInEditMode) {
            setUsername("Mopsalarm", 1)
        }
    }

    @SuppressLint("SetTextI18n")
    fun setUsername(name: String, mark: Int, op: Boolean = false) {
        val userClass = userClassesService.get(mark)

        this.text = SpannableStringBuilder().apply {

            if (op) {
                val badge = OpDrawable(context, textSize)
                badge.setBounds(0, 0, badge.intrinsicWidth, badge.intrinsicHeight)

                inSpans(ImageSpan(badge, ImageSpan.ALIGN_BASELINE)) {
                    append("OP")
                }

                append("  ")
            }

            append(name)
            append("\u2009")

            inSpans(ForegroundColorSpan(userClass.color)) {
                append(userClass.symbol)
            }
        }
    }
}

class OpDrawable(private val context: Context, textSize: Float) : BaseDrawable(PixelFormat.TRANSLUCENT) {
    private val paint = paint { }

    private val outerR = Rect().also {
        paint.textSize = textSize
        paint.getTextBounds("OP", 0, 2, it)
    }.toRectF()

    private val innerR = Rect().also {
        paint.textSize = 0.8f * textSize
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.getTextBounds("OP", 0, 2, it)
    }

    private val accentColor = context.getColorCompat(ThemeHelper.accentColor)
    private val textColor = blendColors(0.2f, Color.WHITE, accentColor)

    private val radius = context.dip2px(2f)
    private val padding = context.dip2px(2f)

    override fun getIntrinsicWidth(): Int {
        return (outerR.width() + 2f * padding).toInt()
    }

    override fun getIntrinsicHeight(): Int {
        return (outerR.height() + 2f * padding).toInt()
    }

    override fun draw(canvas: Canvas) {
        val bounds = this.bounds.toRectF()

        val top = bounds.bottom - padding - outerR.height()
        val bottom = bounds.bottom + padding

        paint.color = accentColor
        canvas.drawRoundRect(bounds.left, top, bounds.right, bottom, radius, radius, paint)

        val textX = (bounds.width() - innerR.width()) / 2f
        val textY = bottom - padding - (outerR.height() - innerR.height()) / 2f

        paint.color = textColor
        canvas.drawText("OP", textX, textY, paint)
    }
}
