package com.pr0gramm.app.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 */
public class BackgroundBitmapDrawable extends android.support.v7.graphics.drawable.DrawableWrapper {
    private float sourceAspect;

    public BackgroundBitmapDrawable(Drawable drawable) {
        super(drawable);
    }

    @Override
    public void setWrappedDrawable(Drawable drawable) {
        super.setWrappedDrawable(drawable);
        sourceAspect = (float) drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        // get new bounds. Try to go with aspect (or original bottom if larger)
        int bottom = bounds.top + (int) (bounds.width() / sourceAspect);
        getWrappedDrawable().setBounds(
                bounds.left, bounds.top,
                bounds.right, Math.max(bounds.bottom, bottom));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.clipRect(getBounds());
        super.draw(canvas);
    }
}