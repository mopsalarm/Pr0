package com.pr0gramm.app.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import com.pr0gramm.app.services.Graph
import com.pr0gramm.app.util.save

/**
 */
class GraphDrawable(private val graph: Graph) : Drawable() {
    var lineColor = Color.WHITE
    var lineWidth = 2f
    var fillColor = Color.TRANSPARENT
    var highlightFillColor = Color.BLUE
    var textSize: Float? = null

    var highlights = arrayListOf<Highlight>()

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(cf: ColorFilter?) {}

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() < 5 || bounds.height() < 5)
            return

        if (graph.isEmpty)
            return

        val sc = Scaling(bounds)

        val path = newGraphPath(sc)

        canvas.save {
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())

            val paint = Paint()
            paint.isAntiAlias = true

            if (fillColor != Color.TRANSPARENT) {
                paint.color = fillColor
                paint.style = Paint.Style.FILL

                paint.shader = LinearGradient(0f, 0f, 0f, sc.bounds.height().toFloat(),
                        fillColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)

                canvas.drawPath(path, paint)

                paint.shader = null

            }

            if (lineColor != Color.TRANSPARENT) {
                paint.color = lineColor
                paint.strokeWidth = lineWidth
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
            }

            highlights.forEach { hi ->
                val point = graph[hi.index]
                val cX = sc.x(point.x).toFloat()
                val cY = sc.y(point.y).toFloat()

                paint.strokeWidth = 0f
                paint.color = highlightFillColor
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cX, cY, 1f * lineWidth, paint)

                paint.strokeWidth = lineWidth
                paint.color = lineColor
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(cX, cY, 1.5f * lineWidth, paint)

                val textSize = textSize ?: 3.5f * lineWidth

                // left or right of highlight?
                var textAlignX: Float
                if (point.x > (graph.first.x + graph.last.x) / 2) {
                    textAlignX = -1f
                } else {
                    textAlignX = 1f
                }

                var baseOffsetX = 2 * lineWidth

                // check if the path goes up or down (moving away from the highlighted point)
                // and decide where to move the point.
                val goesUp = graph[hi.index + textAlignX.toInt()].y >= point.y
                var textAlignY = if (goesUp) 1f else -1f

                // if we want to paint the text too near too the top or too
                // far near to the bottom, we'll flip it to the other side of the line
                if (Math.min(cY, sc.bounds.height() - cY) < 3 * textSize) {
                    textAlignX *= -1
                    textAlignY *= -1
                    baseOffsetX = 0f
                }

                paint.style = Paint.Style.FILL
                paint.color = lineColor
                paint.textSize = textSize

                val textWidth = paint.measureText(hi.text)

                // move anchor to left or right end of the text
                var textX = cX + textWidth * textAlignX.coerceAtMost(0f)

                // add a little offset to the point
                textX += baseOffsetX * textAlignX

                var textY = cY + textSize * textAlignY.coerceAtLeast(-0.2f)
                textY += 2 * lineWidth * textAlignY

                canvas.drawText(hi.text, textX, textY, paint)
            }
        }
    }

    private fun newGraphPath(sc: Scaling): Path {
        // add a point the the start and end of the graph
        val points = listOf(Graph.Point(sc.minX, graph.first.y)) + graph.points + Graph.Point(sc.maxX, graph.last.y)

        val path = Path()

        var previous: Float? = null
        var previousY = 0.0
        for (current in points) {
            val x = sc.x(current.x).toFloat()
            val y = sc.y(current.y).toFloat()


            if (previous == null) {
                path.moveTo(x, y)
                previous = x

            } else if (x - previous > 1 || Math.abs(y - previousY) > 100) {
                path.lineTo(x, y)
                previous = x
                previousY = current.y
            }
        }

        // now close the path
        path.rLineTo(10f, 0f)
        path.rLineTo(0f, sc.bounds.height().toFloat())
        path.rLineTo((-sc.bounds.width() - 20).toFloat(), 0f)
        path.close()
        return path
    }

    private inner class Scaling(val bounds: Rect) {
        val padding = 0.1 * (graph.maxValue - graph.minValue)
        val minY = graph.minValue - padding
        val maxY = graph.maxValue + padding
        val minX = graph.range.start
        val maxX = graph.range.endInclusive

        val scaleX = (maxX - minX) / bounds.width()
        val scaleY = (maxY - minY) / bounds.height()

        fun x(x: Double): Double = (x - minX) / scaleX

        fun y(y: Double): Double {
            if (scaleY > 0) {
                return bounds.height() - (y - minY) / scaleY
            } else {
                return bounds.height() * 0.5
            }
        }
    }

    class Highlight(val index: Int, val text: String)
}
