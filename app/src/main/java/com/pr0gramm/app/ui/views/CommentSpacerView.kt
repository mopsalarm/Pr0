package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.paint
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.observeChangeEx
import kotlin.math.roundToInt

/**
 */
class CommentSpacerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs) {
    private val basePaddingLeft = paddingLeft

    private val lineWidth = context.dip2px(1f)
    private val lineMargin = context.dip2px(8f)

    private val linePaint: Paint = paint {
        isAntiAlias = false
        color = context.getColorCompat(R.color.comment_line)
        style = Paint.Style.STROKE
        strokeWidth = 0f
    }

    val path = Path()

    var depth: Int by observeChangeEx(-1) { oldValue, newValue ->
        if (oldValue != newValue) {
            val paddingLeft = spaceAtDepth(newValue).toInt()
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            requestLayout()
        }
    }

    init {
        // we need to overwrite the default of the view group.
        setWillNotDraw(false)

        if (isInEditMode) {
            depth = 1
        }
    }

    private fun spaceAtDepth(depth: Int): Float {
        return basePaddingLeft + lineMargin * Math.pow(depth.toDouble(), 1 / 1.2).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, linePaint)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (changed) {
            path.reset()

            val height = (b - t + 1).toFloat()

            for (i in 1 until depth) {
                val x = (spaceAtDepth(i) - lineWidth).toInt().toFloat()

                path.moveTo(x, 0f)
                path.lineTo(x, height)
            }

            linePaint.pathEffect = dashPathEffect(height)
        }
    }

    private fun dashPathEffect(height: Float): DashPathEffect {
        // calculate how many full repetitions of the given size we need.
        val size = context.dip2px(5f)
        val repetitions = (height / (2 * size)).roundToInt()
        val modifiedSize = 0.5f * height / repetitions

        return DashPathEffect(floatArrayOf(modifiedSize, modifiedSize), 0f)
    }
}
