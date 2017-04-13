package com.pr0gramm.app.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import com.google.common.collect.Iterables
import com.pr0gramm.app.services.Graph
import com.pr0gramm.app.util.save

/**
 */
class GraphDrawable(private val graph: Graph) : Drawable() {
    var lineColor = Color.WHITE
    var fillColor = Color.TRANSPARENT

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

        val path = newGraphPath(bounds)

        canvas.save {
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())

            val paint = Paint()
            paint.isAntiAlias = true

            if (fillColor != Color.TRANSPARENT) {
                paint.color = fillColor
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }

            if (lineColor != Color.TRANSPARENT) {
                paint.color = lineColor
                paint.strokeWidth = 2f
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun newGraphPath(bounds: Rect): Path {
        val padding = 0.1 * (graph.maxValue - graph.minValue)
        val minY = graph.minValue - padding
        val maxY = graph.maxValue + padding
        val minX = graph.range.lowerEndpoint()
        val maxX = graph.range.upperEndpoint()

        val scaleX = (maxX - minX) / bounds.width()
        val scaleY = (maxY - minY) / bounds.height()

        // add a point the the start and end of the graph
        val points = Iterables.concat(
                setOf(Graph.Point(minX, graph.first.y)),
                graph.points,
                setOf(Graph.Point(maxX, graph.last.y)))

        val path = Path()

        var previous: Float? = null
        var previousY = 0.0
        for (current in points) {
            val x = ((current.x - minX) / scaleX).toFloat()
            val y = (if (scaleY > 0)
                bounds.height() - (current.y - minY) / scaleY
            else
                bounds.height() * 0.5).toFloat()

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
        path.rLineTo(0f, bounds.height().toFloat())
        path.rLineTo((-bounds.width() - 20).toFloat(), 0f)
        path.close()
        return path
    }
}
