package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.pr0gramm.app.R
import com.pr0gramm.app.util.use

/**
 */
class ScoreUnknownView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val linePaint: Paint = Paint()
    private val radius: Float
    private val path: Path

    init {
        linePaint.isAntiAlias = true
        linePaint.style = Paint.Style.FILL

        radius = context.theme.obtainStyledAttributes(attrs, R.styleable.ScoreUnknownView, 0, 0).use { a ->
            linePaint.color = a.getColor(R.styleable.ScoreUnknownView_fillColor, 0x808080)
            a.getDimension(R.styleable.ScoreUnknownView_radius, 10f)
        }

        setWillNotDraw(false)

        path = Path().apply {
            val r = radius
            val y = radius
            addCircle(1 * r, y, 0.9f * r, Path.Direction.CW)
            addCircle(3 * r, y, 0.9f * r, Path.Direction.CW)
            addCircle(5 * r, y, 0.9f * r, Path.Direction.CW)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
                Math.ceil((6 * radius).toDouble()).toInt(),
                Math.ceil((2 * radius).toDouble()).toInt())
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, linePaint)
    }
}
