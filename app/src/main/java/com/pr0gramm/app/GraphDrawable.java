package com.pr0gramm.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.google.common.collect.Iterables;

import static java.util.Collections.singleton;

/**
 */
public class GraphDrawable extends Drawable {
    private final Graph graph;

    public GraphDrawable(Graph graph) {
        this.graph = graph;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() < 5 || bounds.height() < 5)
            return;

        if (graph.isEmpty())
            return;

        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        // draw in a bright color. This should be customizable
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setAntiAlias(true);

        float padding = 0.1f * (graph.maxValue() - graph.minValue());
        float minY = graph.minValue() - padding;
        float maxY = graph.maxValue() + padding;
        float minX = graph.range().lowerEndpoint();
        float maxX = graph.range().upperEndpoint();

        float scaleX = (maxX - minX) / bounds.width();
        float scaleY = (maxY - minY) / bounds.height();

        // add a point the the start and end of the graph
        Iterable<PointF> points = Iterables.concat(
                singleton(new PointF(minX, graph.first().y)),
                graph.points(),
                singleton(new PointF(maxX, graph.last().y)));

        PointF previous = graph.first();
        for (PointF current : Iterables.skip(points, 1)) {
            float x1 = (previous.x - minX) / scaleX;
            float y1 = bounds.height() - (previous.y - minY) / scaleY;

            float x2 = (current.x - minX) / scaleX;
            float y2 = bounds.height() - (current.y - minY) / scaleY;

            canvas.drawLine(x1, y1, x2, y2, paint);
            previous = current;
        }

        canvas.restore();
    }

}
