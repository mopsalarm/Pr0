package com.pr0gramm.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
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

    private int lineColor = Color.WHITE;
    private int fillColor = Color.TRANSPARENT;

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

        Path path = newGraphPath(bounds);

        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        if (fillColor != Color.TRANSPARENT) {
            paint.setColor(fillColor);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
        }

        if (lineColor != Color.TRANSPARENT) {
            paint.setColor(lineColor);
            paint.setStrokeWidth(2);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        }

        canvas.restore();
    }

    private Path newGraphPath(Rect bounds) {
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

        Path path = new Path();

        Float previous = null;
        for (PointF current : points) {
            float x = (current.x - minX) / scaleX;
            float y = scaleY > 0
                    ? bounds.height() - (current.y - minY) / scaleY
                    : bounds.height() * 0.5f;

            if (previous == null) {
                path.moveTo(x, y);
                previous = x;

            } else if (x - previous > 1) {
                path.lineTo(x, y);
                previous = x;
            }
        }

        // now close the path
        path.rLineTo(10, 0);
        path.rLineTo(0, bounds.height());
        path.rLineTo(-bounds.width() - 20, 0);
        path.close();
        return path;
    }

    public void setLineColor(int lineColor) {
        this.lineColor = lineColor;
    }

    public void setFillColor(int fillColor) {
        this.fillColor = fillColor;
    }

    public void noLineColor() {
        setLineColor(Color.TRANSPARENT);
    }

    public void noFillColor() {
        setFillColor(Color.TRANSPARENT);
    }
}
