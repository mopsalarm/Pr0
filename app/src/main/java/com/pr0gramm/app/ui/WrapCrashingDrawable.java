package com.pr0gramm.app.ui;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v7.graphics.drawable.DrawableWrapper;

/**
 * There is a bug on some Samsung devices. Those devices are crashing if they are
 * painting a round gradient drawable. Say whaaat!? Okay, we just catch a crash
 * like that and paint again with the fallback color.
 */
class WrapCrashingDrawable extends DrawableWrapper {
    @ColorInt
    private final int fallbackColor;

    public WrapCrashingDrawable(@ColorInt int fallbackColor, Drawable drawable) {
        super(drawable);
        this.fallbackColor = fallbackColor;
    }

    @Override
    public void draw(Canvas canvas) {
        try {
            super.draw(canvas);
        } catch (Exception ignored) {
            canvas.drawColor(fallbackColor);
        }
    }
}
