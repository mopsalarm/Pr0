package com.pr0gramm.app.ui.fragments

import android.app.Activity
import android.graphics.PointF
import android.view.View
import com.pr0gramm.app.util.AndroidUtility.screenSize

/**
 * Fullscreen parameters for a viewer. This is used with [PostFragment]
 */
class ViewerFullscreenParameters private constructor(val scale: Float, val trY: Float, val pivot: PointF, val rotation: Float) {
    companion object {
        fun forViewer(activity: Activity, viewer: View): ViewerFullscreenParameters {
            val screenSize = screenSize(activity)

            val windowWidth = screenSize.x.toFloat()
            val windowHeight = screenSize.y.toFloat()

            val viewerAspect = viewer.width.toFloat() / (viewer.measuredHeight - viewer.paddingTop).toFloat()
            val viewerWidth = windowWidth
            val viewerHeight = windowWidth / viewerAspect

            val pivot = PointF(viewerWidth / 2f, viewerHeight - 0.5f * viewerHeight + viewer.paddingTop)
            val trY = windowHeight / 2f - pivot.y

            val scaleRot = Math.min(
                    windowHeight / viewerWidth,
                    windowWidth / viewerHeight)

            val scaleNoRot = Math.min(
                    windowHeight / viewerHeight,
                    windowWidth / viewerWidth)

            // check if rotation is necessary
            if (scaleRot > scaleNoRot) {
                return ViewerFullscreenParameters(scaleRot, trY, pivot, 90f)
            } else {
                return ViewerFullscreenParameters(scaleNoRot, trY, pivot, 0f)
            }
        }
    }

}
