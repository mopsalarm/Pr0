package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.util.observeChange

/**
 */
class CommentSpacerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs) {
    private var lineMargin: Float = 0f
    private val lineWidth: Float
    private val linePaint: Paint

    var depth: Int by observeChange(0) {
        val paddingLeft = (lineMargin * depth).toInt()
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

        invalidate()
        requestLayout()
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

        linePaint = Paint().apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = lineWidth
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val path = Path()
        for (i in 1..depth - 1) {
            val x = i * lineMargin - lineWidth

            path.moveTo(x, 0f)
            path.lineTo(x, height.toFloat())
        }

        canvas.drawPath(path, linePaint)
    }
}
