package com.pr0gramm.app.services

import com.google.common.collect.Range

/**
 * A simple graph of double values.
 */
class Graph(start: Double, end: Double,
            val points: List<Graph.Point>) {

    val range = Range.closed(start, end)

    val first
        get() = points[0]

    val last
        get() = points[points.size - 1]

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
}
