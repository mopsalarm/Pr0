package com.pr0gramm.app.ui.views.viewer.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View

/**
 * [ViewBackend] that uses a [TextureView]
 */
internal class TextureViewBackend(context: Context,
                                  private val callbacks: ViewBackend.Callbacks) : ViewBackend, TextureView.SurfaceTextureListener {

    private val textureView: TextureView = TextureView(context)

    init {
        this.textureView.surfaceTextureListener = this
    }

    override var currentSurface: Surface? = null

    override var size = ViewBackend.Size(0, 0)

    override val view: View get() = textureView

    @SuppressLint("Recycle")
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        currentSurface = Surface(surface)

        callbacks.onAvailable(this)
        callbacks.onSizeChanged(this, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        callbacks.onSizeChanged(this, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        callbacks.onDestroy(this)

        this.currentSurface?.release()
        this.currentSurface = null

        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
