package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.CircleChartViewBinding
import com.pr0gramm.app.ui.BaseDrawable
import com.pr0gramm.app.ui.paint
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use

class CircleChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AspectLayout(context, attrs, defStyleAttr) {
    private val views = CircleChartViewBinding.inflate(layoutInflater, this)

    private val viewLines = listOf(
            views.line1, views.line2, views.line3,
    )

    private val chart = CircleChartDrawable()

    var chartValues: List<Value> by observeChange(listOf()) {
        val score = chartValues.sumOf { it.amount }
        views.value.text = formatScore(score)
        chart.invalidateSelf()
    }

    private inner class CircleChartDrawable : BaseDrawable(PixelFormat.TRANSLUCENT) {
        override fun draw(canvas: Canvas) {
            val bounds = bounds

            val totalValue = chartValues.sumOf { Math.abs(it.amount) }
            if (totalValue == 0 || bounds.width() < 5 || bounds.height() < 5)
                return

            val lineWidth = context.dp(4f)

            val paint = paint {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth
            }

            val offset = 0.75f * paint.strokeWidth
            val boundsArc = RectF(bounds.left + offset, bounds.top + offset, bounds.bottom - offset, bounds.right - offset)

            val boundsInnerArc = RectF(
                    boundsArc.left + 0.9f * lineWidth,
                    boundsArc.top + 0.9f * lineWidth,
                    boundsArc.bottom - 0.9f * lineWidth,
                    boundsArc.right - 0.9f * lineWidth)

            val angleStep = 5f
            val totalAngle = 360f - angleStep * chartValues.count { it.amount != 0 }

            var currentAngle = -15f
            chartValues.filter { it.amount != 0 }.forEach { value ->
                val angle = totalAngle * (Math.abs(value.amount) / totalValue.toFloat())

                paint.color = Color.argb(64, 0, 0, 0)
                canvas.drawArc(boundsInnerArc, currentAngle, angle, false, paint)

                paint.color = value.color
                canvas.drawArc(boundsArc, currentAngle, angle, false, paint)

                currentAngle += angle + angleStep
            }
        }
    }

    class Value(val amount: Int, @ColorInt val color: Int)

    init {
        aspect = 1f
        context.theme.obtainStyledAttributes(attrs, R.styleable.CircleChartView, 0, 0).use {
            viewLines[0].text = it.getString(R.styleable.CircleChartView_lineTop) ?: ""
            viewLines[2].text = it.getString(R.styleable.CircleChartView_lineBottom) ?: ""

            viewLines[1].text = it.getString(R.styleable.CircleChartView_chartType) ?: "POST"
        }
        ViewCompat.setBackground(this, chart)
    }
}

fun formatScore(value: Int): String {
    val abs = Math.abs(value)
    return when {
        abs >= 1000_000 -> "%1.2fm".format(value / 1000000f)

        abs >= 100_000 -> "%1.0fk".format(value / 1000f)
        abs >= 10_000 -> "%1.1fk".format(value / 1000f)
        abs >= 1_000 -> "%1.2fk".format(value / 1000f)
        else -> value.toString()
    }
}

