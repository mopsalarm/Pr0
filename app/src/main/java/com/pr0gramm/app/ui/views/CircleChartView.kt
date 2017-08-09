package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.ui.BaseDrawable
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use
import kotterknife.bindView

class CircleChartView : AspectLayout {
    private val viewValue: TextView by bindView(R.id.value)
    private val viewType: TextView by bindView(R.id.line2)

    private val chart = CircleChartDrawable()

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        inflate(context, R.layout.circle_chart_view, this)

        aspect = 1f

        context.theme.obtainStyledAttributes(attrs, R.styleable.CircleChartView, 0, 0).use {
            viewType.text = it.getString(R.styleable.CircleChartView_chartType) ?: "POST"
        }

        ViewCompat.setBackground(this, chart)
    }

    var voteSummary: VoteService.Summary by observeChange(VoteService.Summary(0, 0, 0)) {
        val totalVoteCount = voteSummary.fav + voteSummary.up - voteSummary.down
        viewValue.text = formatScore(totalVoteCount)

        chart.invalidateSelf()
    }

    private inner class CircleChartDrawable : BaseDrawable(PixelFormat.TRANSLUCENT) {
        override fun draw(canvas: Canvas) {
            val bounds = bounds

            val totalVoteCount = voteSummary.down + voteSummary.fav + voteSummary.up
            if (totalVoteCount == 0 || bounds.width() < 5 || bounds.height() < 5)
                return

            val lineWidth = AndroidUtility.dp(context, 4).toFloat()

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = lineWidth
            }

            val colors = listOf(R.color.stats_up, R.color.stats_down, R.color.stats_fav)
            val values = listOf(voteSummary.up, voteSummary.down, voteSummary.fav)

            val offset = 0.75f * paint.strokeWidth
            val boundsArc = RectF(bounds.left + offset, bounds.top + offset, bounds.bottom - offset, bounds.right - offset)

            val angleStep = 5f
            val totalAngle = 360f - angleStep * values.count { it != 0 }

            var currentAngle = -15f
            values.zip(colors).filter { it.first != 0 }.forEach { (voteCount, color) ->
                val angle = totalAngle * (voteCount / totalVoteCount.toFloat())

                paint.color = context.getColorCompat(color)
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
}

fun formatScore(value: Int): String {
    return when {
        value >= 1000_000 -> "%1.2fm".format(value / 1000000f)

        value >= 100_000 -> "%1fk".format(value / 1000f)
        value >= 10_000 -> "%1.1fk".format(value / 1000f)
        value >= 1_000 -> "%1.2fk".format(value / 1000f)
        else -> value.toString()
    }
}

