package com.pr0gramm.app.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

import com.pr0gramm.app.feed.ContentType;

/**
 * A simple triangle drawable
 */
public class TriangleDrawable extends Drawable {
    private final Paint paint;
    private final int size;

    private TriangleDrawable(@ColorInt int color, int size) {
        this.size = size;

        paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
    }

    public TriangleDrawable(ContentType contentType, int size) {
        this(colorForContentType(contentType), size);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();

        Path path = new Path();
        path.moveTo(bounds.left, bounds.bottom);
        path.lineTo(bounds.left, bounds.bottom - size);
        path.lineTo(bounds.left + size, bounds.bottom);
        path.lineTo(bounds.left, bounds.bottom);

        canvas.drawPath(path, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }


    /**
     * Gets a color for the given content type
     */
    @ColorInt
    private static int colorForContentType(ContentType contentType) {
        switch (contentType) {
            case SFW:
                return Color.parseColor("#a7d713");
            case NSFW:
                return Color.parseColor("#f6ab09");
            case NSFL:
                return Color.parseColor("#d9534f");
            case NSFP:
                return Color.parseColor("#69ccca");
            default:
                return Color.GRAY;
        }
    }
}
