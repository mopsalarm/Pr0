package com.pr0gramm.app.ui.views.viewer;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.graphics.drawable.DrawableWrapper;

/**
 * A drawale that centers a child drawable.
 */
class CenterDrawable extends DrawableWrapper {
    public CenterDrawable(Drawable drawable) {
        super(drawable);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        Rect wrapped = getWrappedDrawable().getBounds();

        float innerAspect = wrapped.width() / (float) wrapped.height();
        float outerAspect = bounds.width() / (float) bounds.height();

        Rect result = new Rect(bounds);
        if (innerAspect > outerAspect) {
            result.top = (int) (bounds.exactCenterY() - 0.5f * bounds.width() / innerAspect);
            result.bottom = (int) (bounds.exactCenterY() + 0.5f * bounds.width() / innerAspect);
        } else {
            result.left = (int) (bounds.exactCenterX() - 0.5f * bounds.height() / innerAspect);
            result.right = (int) (bounds.exactCenterX() + 0.5f * bounds.height() / innerAspect);
        }

        getWrappedDrawable().setBounds(result);
    }
}
