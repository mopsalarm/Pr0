package com.pr0gramm.app.services


/**
 * A simple graph of double values.
 */
data class Graph(val firstX: Double, val lastX: Double, val points: List<Graph.Point>) {

    constructor(points: List<Graph.Point>) : this(points.first().x, points.last().x, points)

    val range = firstX.rangeTo(lastX)

    val first get() = points.first()

    val last get() = points.last()

    val isEmpty: Boolean
        get() = points.isEmpty()

    val maxValue: Double get() {
        return points.maxBy { it.y }!!.y
    }

    val minValue: Double get() {
        return points.minBy { it.y }!!.y
    }

    operator fun get(idx: Int): Point {
        return points[idx]
    }


    data class Point(val x: Double, val y: Double)

    fun sampleEquidistant(steps: Int, start: Double = range.start, end: Double = range.endInclusive): Graph {
        return Graph((0 until steps).map { idx ->
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

fun <T> optimizeValuesBy(values: List<T>, get: (T) -> Double): List<T> {
    return values.filterIndexed { idx, value ->
        val v = get(value)
        idx == 0 || idx == values.size - 1 || get(values[idx - 1]) != v || v != get(values[idx + 1])
    }
}
