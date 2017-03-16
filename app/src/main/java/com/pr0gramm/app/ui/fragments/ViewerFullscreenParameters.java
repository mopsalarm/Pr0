package com.pr0gramm.app.ui.fragments;

import android.app.Activity;
import android.graphics.Point;
import android.view.View;

import com.pr0gramm.app.util.AndroidUtility;

/**
 * Fullscreen parameters for a viewer. This is used with {@link PostFragment}
 */
class ViewerFullscreenParameters {
    final float scale;
    final float trY;
    final float rotation;

    private ViewerFullscreenParameters(float scale, float trY, float rotation) {
        this.scale = scale;
        this.trY = trY;
        this.rotation = rotation;
    }

    static ViewerFullscreenParameters forViewer(Activity activity, View viewer) {
        Point screenSize = AndroidUtility.screenSize(activity);

        int windowWidth = screenSize.x;
        float windowHeight = screenSize.y;

        //noinspection UnnecessaryLocalVariable
        int viewerWidth = viewer.getWidth();
        int viewerHeight = viewer.getHeight() - viewer.getPaddingTop();

        viewer.setPivotY(viewer.getHeight() - 0.5f * viewerHeight);
        viewer.setPivotX(viewerWidth / 2.f);
        final float trY = (windowHeight / 2.f - viewer.getPivotY());

        float scaleRot = Math.min(
                windowHeight / (float) viewerWidth,
                windowWidth / (float) viewerHeight);

        float scaleNoRot = Math.min(
                windowHeight / (float) viewerHeight,
                windowWidth / (float) viewerWidth);

        // check if rotation is necessary
        if (scaleRot > scaleNoRot) {
            return new ViewerFullscreenParameters(scaleRot, trY, 90.f);
        } else {
            return new ViewerFullscreenParameters(scaleNoRot, trY, 0.f);
        }
    }

}
