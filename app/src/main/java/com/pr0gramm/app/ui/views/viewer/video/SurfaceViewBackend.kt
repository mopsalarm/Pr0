package com.pr0gramm.app.ui.views.viewer.video

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View

/**
 */
internal class SurfaceViewBackend(
        context: Context, private val callbacks: ViewBackend.Callbacks) : ViewBackend, SurfaceHolder.Callback {

    private val surfaceView: SurfaceView = SurfaceView(context)

    init {
        this.surfaceView.holder.addCallback(this)
    }

    override val currentSurface: Surface?
        get() = surfaceView.holder.surface

    override val hasSurface: Boolean
        get() = surfaceView.holder.surface?.isValid ?: false

    override var size = ViewBackend.Size(0, 0)
        set(value) {
            field = value
            surfaceView.holder.setFixedSize(value.width, value.height)
        }

    override val view: View get() = surfaceView

    override fun surfaceCreated(holder: SurfaceHolder) {
        callbacks.onAvailable(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        callbacks.onSizeChanged(this, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        callbacks.onDestroy(this)
    }

}
