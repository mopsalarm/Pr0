package com.pr0gramm.app.ui.fragments

import android.app.Activity
import android.graphics.PointF
import android.view.View
import com.pr0gramm.app.util.AndroidUtility.screenSize
import kotlin.math.min

/**
 * Fullscreen parameters for a viewer. This is used with [PostFragment]
 */
class ViewerFullscreenParameters private constructor(scale: Float, val trY: Float, val pivot: PointF, val rotation: Float) {
    // work against broken calculations
    val scale: Float = if (scale.isNaN()) 1f else scale

    companion object {
        fun forViewer(activity: Activity, viewer: View, rotateIfNeeded: Boolean): ViewerFullscreenParameters {
            val screenSize = screenSize(activity)

            val windowWidth = screenSize.x.toFloat()
            val windowHeight = screenSize.y.toFloat()
            val windowAspect = windowWidth / windowHeight

            val viewerAspect = viewer.width.toFloat() / (viewer.measuredHeight - viewer.paddingTop).toFloat()

            val (viewerWidth, viewerHeight) = if (!rotateIfNeeded && windowAspect > viewerAspect) {
                // landscape
                Pair(windowHeight * viewerAspect, windowHeight)

            } else {
                // portrait
                Pair(windowWidth, (windowWidth / viewerAspect))
            }

            val pivot = PointF(viewerWidth / 2f, viewerHeight - 0.5f * viewerHeight + viewer.paddingTop)
            val trY = windowHeight / 2f - pivot.y

            val scaleRot = min(
                    windowHeight / viewerWidth,
                    windowWidth / viewerHeight)

            val scaleNoRot = min(
                    windowHeight / viewerHeight,
                    windowWidth / viewerWidth)

            // check if rotation is necessary
            if (scaleRot > scaleNoRot && rotateIfNeeded) {
                return ViewerFullscreenParameters(scaleRot, trY, pivot, 90f)
            } else {
                return ViewerFullscreenParameters(scaleNoRot, trY, pivot, 0f)
            }
        }
    }
}
