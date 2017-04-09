package com.pr0gramm.app.ui.views.viewer.video

import android.view.Surface
import android.view.View

/**
 * Interface for a video backend.
 */
internal interface ViewBackend {
    var size: Size

    val currentSurface: Surface?

    val view: View

    val hasSurface: Boolean get() = currentSurface != null

    data class Size(val width: Int, val height: Int)

    /**
     * Callbacks that are send from a [ViewBackend].
     */
    interface Callbacks {
        fun onAvailable(backend: ViewBackend)

        fun onSizeChanged(backend: ViewBackend, width: Int, height: Int)

        fun onDestroy(backend: ViewBackend)
    }
}
