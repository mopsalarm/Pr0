package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.paint

/**
 */
class CommentSpacerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs) {
    private var lineMargin: Float = 0f
    private val lineWidth: Float
    private val linePaint: Paint

    private val config: Config = ConfigService.get(context)

    private val basePaddingLeft = paddingLeft

    val path = Path()

    var depth: Int = 0; set(value) {
        field = value.coerceAtMost(config.commentsMaxLevels)

        val paddingLeft = spaceAtDepth(field).toInt()
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    private fun spaceAtDepth(depth: Int): Float {
        return basePaddingLeft + lineMargin * Math.pow(depth.toDouble(), 1 / 1.2).toFloat()
    }

    init {
        setWillNotDraw(false)

        // get values from attributes.
        val lineColor: Int
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.CommentSpacerView, 0, 0)
        try {
            depth = a.getInt(R.styleable.CommentSpacerView_depth, 1)
            lineColor = a.getColor(R.styleable.CommentSpacerView_lineColor, 1)
            lineMargin = a.getDimension(R.styleable.CommentSpacerView_lineMargin, 10f)
            lineWidth = a.getDimension(R.styleable.CommentSpacerView_lineWidth, 2f)
        } finally {
            a.recycle()
        }

        linePaint = paint {
            isAntiAlias = false
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = lineWidth
            pathEffect = DASH_PATH_EFFECT
        }

        if (isInEditMode) {
            depth = 1
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, linePaint)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (changed) {
            path.reset()

            for (i in 1 until depth) {
                val x = spaceAtDepth(i) - lineWidth

                path.moveTo(x, 0f)
                path.lineTo(x, (b - t).toFloat())
            }
        }
    }

    companion object {
        @JvmStatic
        private val DASH_PATH_EFFECT = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
}
