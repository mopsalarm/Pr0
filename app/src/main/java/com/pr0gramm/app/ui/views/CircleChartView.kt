package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.support.annotation.ColorInt
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.BaseDrawable
import com.pr0gramm.app.ui.paint
import com.pr0gramm.app.util.dp2px
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use
import kotterknife.bindView
import kotterknife.bindViews

class CircleChartView : AspectLayout {
    private val viewValue: TextView by bindView(R.id.value)
    private val viewLines: List<TextView> by bindViews(R.id.line1, R.id.line2, R.id.line3)

    private val chart = CircleChartDrawable()

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        inflate(context, R.layout.circle_chart_view, this)

        aspect = 1f

        context.theme.obtainStyledAttributes(attrs, R.styleable.CircleChartView, 0, 0).use {
            viewLines[0].text = it.getString(R.styleable.CircleChartView_lineTop) ?: ""
            viewLines[2].text = it.getString(R.styleable.CircleChartView_lineBottom) ?: ""

            viewLines[1].text = it.getString(R.styleable.CircleChartView_chartType) ?: "POST"
        }

        ViewCompat.setBackground(this, chart)
    }

    var chartValues: List<Value> by observeChange(listOf()) {
        val score = chartValues.sumBy { it.amount }
        viewValue.text = formatScore(score)
        chart.invalidateSelf()
    }

    private inner class CircleChartDrawable : BaseDrawable(PixelFormat.TRANSLUCENT) {
        override fun draw(canvas: Canvas) {
            val bounds = bounds

            val totalValue = chartValues.sumBy { Math.abs(it.amount) }
            if (totalValue == 0 || bounds.width() < 5 || bounds.height() < 5)
                return

            val lineWidth = context.dp2px(4f)

            val paint = paint {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth
            }

            val offset = 0.75f * paint.strokeWidth
            val boundsArc = RectF(bounds.left + offset, bounds.top + offset, bounds.bottom - offset, bounds.right - offset)

            val angleStep = 5f
            val totalAngle = 360f - angleStep * chartValues.count { it.amount != 0 }

            var currentAngle = -15f
            chartValues.filter { it.amount != 0 }.forEach { value ->
                val angle = totalAngle * (Math.abs(value.amount) / totalValue.toFloat())

                paint.color = value.color
                canvas.drawArc(boundsArc, currentAngle, angle, false, paint)
                currentAngle += angle + angleStep
            }

            // paint inner circle
            val circleRadius = boundsArc.width() / 2 - 3f * lineWidth
            if (circleRadius > 1) {
                // draw middle circle
                paint.style = Paint.Style.FILL
                paint.color = 0xff333333L.toInt()
                canvas.drawCircle(boundsArc.centerX(), boundsArc.centerY(), circleRadius, paint)
            }
        }
    }

    class Value(val amount: Int, @ColorInt val color: Int)
}

fun formatScore(value: Int): String {
    return when {
        value >= 1000_000 -> "%1.2fm".format(value / 1000000f)

        value >= 100_000 -> "%1.0fk".format(value / 1000f)
        value >= 10_000 -> "%1.1fk".format(value / 1000f)
        value >= 1_000 -> "%1.2fk".format(value / 1000f)
        else -> value.toString()
    }
}

