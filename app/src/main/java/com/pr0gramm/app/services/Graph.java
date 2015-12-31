package com.pr0gramm.app.services;

import com.google.common.collect.Range;

import java.util.List;

/**
 * A simple value graph.
 */
public class Graph {
    private final Range<Double> range;
    private final List<Point> points;

    public Graph(double start, double end, List<Point> points) {
        this.range = Range.closed(start, end);
        this.points = points;
    }

    public Range<Double> range() {
        return range;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public double maxValue() {
        double max = points.get(0).y;
        for (Point point : points)
            max = Math.max(max, point.y);

        return max;
    }

    public double minValue() {
        double min = points.get(0).y;
        for (Point point : points)
            min = Math.min(min, point.y);

        return min;
    }

    public List<Point> points() {
        return points;
    }

    public Point get(int idx) {
        return points.get(idx);
    }

    public Point first() {
        return points.get(0);
    }

    public Point last() {
        return points.get(points.size() - 1);
    }

    public static class Point {
        public final double x;
        public final double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
