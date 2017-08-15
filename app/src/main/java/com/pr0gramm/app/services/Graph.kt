package com.pr0gramm.app.services


/**
 * A simple graph of double values.
 */
class Graph(start: Double, end: Double,
            val points: List<Graph.Point>) {

    constructor(points: List<Graph.Point>) : this(points.first().x, points.last().x, points) {
    }

    val range = start.rangeTo(end)

    val first get() = points.first()

    val last get() = points.last()

    val isEmpty: Boolean
        get() = points.isEmpty()

    val maxValue: Double get() {
        var max = points[0].y
        for (point in points)
            max = Math.max(max, point.y)

        return max
    }

    val minValue: Double get() {
        var min = points[0].y
        for (point in points)
            min = Math.min(min, point.y)

        return min
    }

    operator fun get(idx: Int): Point {
        return points[idx]
    }


    class Point(val x: Double, val y: Double)

    fun sampleEquidistant(steps: Int, start: Double = range.start, end: Double = range.endInclusive): Graph {
        return Graph((0..steps - 1).map { idx ->
            // the x position that is at the sampling point
            val x = start + (end - start) * idx / (steps - 1)
            val y = valueAt(x)

            Point(x, y)
        })
    }

    fun valueAt(x: Double): Double {
        // find first point that is right of our query point.
        val largerIndex = points.indexOfFirst { it.x >= x }

        if (largerIndex == -1) {
            // we did not found a point that is right of x, so we take the value of the
            // right most point we know.
            return last.y
        }

        if (largerIndex == 0) {
            // the left-most point is already right of x, so take value of the first point.
            return first.y
        }

        // get points a and b.
        val a = points[largerIndex - 1]
        val b = points[largerIndex]

        // interpolate the value at x between a.x and b.x using m as the ascend
        val m = (b.y - a.y) / (b.x - a.x)
        return a.y + m * (x - a.x)
    }
}

fun <T> optimizeValuesBy(values: List<T>, get: (T) -> Any): List<T> {
    return values.filterIndexed { idx, value ->
        val v = get(value)
        idx == 0 || idx == values.size - 1 || get(values[idx - 1]) != v || v != get(values[idx + 1])
    }
}
