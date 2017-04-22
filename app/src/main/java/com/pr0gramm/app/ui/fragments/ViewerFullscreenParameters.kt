package com.pr0gramm.app.ui.fragments

import android.app.Activity
import android.view.View
import com.pr0gramm.app.util.AndroidUtility.screenSize

/**
 * Fullscreen parameters for a viewer. This is used with [PostFragment]
 */
class ViewerFullscreenParameters private constructor(val scale: Float, val trY: Float, val rotation: Float) {
    companion object {
        @JvmStatic
        fun forViewer(activity: Activity, viewer: View): ViewerFullscreenParameters {
            val screenSize = screenSize(activity)

            val windowWidth = screenSize.x
            val windowHeight = screenSize.y.toFloat()

            val viewerWidth = viewer.width
            val viewerHeight = viewer.height - viewer.paddingTop

            viewer.pivotY = viewer.height - 0.5f * viewerHeight
            viewer.pivotX = viewerWidth / 2f
            val trY = windowHeight / 2f - viewer.pivotY

            val scaleRot = Math.min(
                    windowHeight / viewerWidth.toFloat(),
                    windowWidth / viewerHeight.toFloat())

            val scaleNoRot = Math.min(
                    windowHeight / viewerHeight.toFloat(),
                    windowWidth / viewerWidth.toFloat())

            // check if rotation is necessary
            if (scaleRot > scaleNoRot) {
                return ViewerFullscreenParameters(scaleRot, trY, 90f)
            } else {
                return ViewerFullscreenParameters(scaleNoRot, trY, 0f)
            }
        }
    }

}
