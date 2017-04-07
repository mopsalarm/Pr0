package com.pr0gramm.app.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.google.common.collect.Iterables;
import com.pr0gramm.app.services.Graph;

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
        double padding = 0.1 * (graph.getMaxValue() - graph.getMinValue());
        double minY = graph.getMinValue() - padding;
        double maxY = graph.getMaxValue() + padding;
        double minX = graph.getRange().lowerEndpoint();
        double maxX = graph.getRange().upperEndpoint();

        double scaleX = (maxX - minX) / bounds.width();
        double scaleY = (maxY - minY) / bounds.height();

        // add a point the the start and end of the graph
        Iterable<Graph.Point> points = Iterables.concat(
                singleton(new Graph.Point(minX, graph.getFirst().getY())),
                graph.getPoints(),
                singleton(new Graph.Point(maxX, graph.getLast().getY())));

        Path path = new Path();

        Float previous = null;
        double previousY = 0;
        for (Graph.Point current : points) {
            float x = (float) ((current.getX() - minX) / scaleX);
            float y = (float) (scaleY > 0
                    ? bounds.height() - (current.getY() - minY) / scaleY
                    : bounds.height() * 0.5);

            if (previous == null) {
                path.moveTo(x, y);
                previous = x;

            } else if (x - previous > 1 || Math.abs(y - previousY) > 100) {
                path.lineTo(x, y);
                previous = x;
                previousY = current.getY();
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
