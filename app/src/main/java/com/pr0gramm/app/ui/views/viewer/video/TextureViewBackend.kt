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
internal class TextureViewBackend(
        context: Context, private val callbacks: Callbacks) : TextureView.SurfaceTextureListener {

    private val textureView = TextureView(context).apply {
        surfaceTextureListener = this@TextureViewBackend
    }

    var currentSurface: Surface? = null
        private set

    val view: View = textureView

    @SuppressLint("Recycle")
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        currentSurface = Surface(surface)

        callbacks.onAvailable(this)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        callbacks.onDestroy(this)

        this.currentSurface?.release()
        this.currentSurface = null

        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    /**
     * Callbacks that are send from a [ViewBackend].
     */
    interface Callbacks {
        fun onAvailable(backend: TextureViewBackend)

        fun onDestroy(backend: TextureViewBackend)
    }
}
