package com.pr0gramm.app.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 */
public class BackgroundBitmapDrawable extends android.support.v7.graphics.drawable.DrawableWrapper {
    private float aspect;

    public BackgroundBitmapDrawable(Drawable drawable) {
        super(drawable);
        aspect = (float) drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
    }

    public void setAspect(float aspect) {
        this.aspect = aspect;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        // get new bounds. Try to go with aspect (or original bottom if larger)
        int bottom = bounds.top + (int) (bounds.width() / aspect);
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