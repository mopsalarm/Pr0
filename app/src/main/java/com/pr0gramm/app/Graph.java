package com.pr0gramm.app;

import android.graphics.PointF;

import com.google.common.collect.Range;

import java.util.List;

/**
 * A simple value graph.
 */
public class Graph {
    private final Range<Float> range;
    private final List<PointF> points;

    public Graph(float start, float end, List<PointF> points) {
        this.range = Range.closed(start, end);
        this.points = points;
    }

    public Range<Float> range() {
        return range;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public float maxValue() {
        float max = points.get(0).y;
        for (PointF point : points)
            max = Math.max(max, point.y);

        return max;
    }

    public float minValue() {
        float min = points.get(0).y;
        for (PointF point : points)
            min = Math.min(min, point.y);

        return min;
    }

    public List<PointF> points() {
        return points;
    }

    public PointF get(int idx) {
        return points.get(idx);
    }

    public PointF first() {
        return points.get(0);
    }

    public PointF last() {
        return points.get(points.size() - 1);
    }
}
