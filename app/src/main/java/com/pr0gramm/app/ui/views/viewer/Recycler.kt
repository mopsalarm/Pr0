package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.metadata.MetadataOutput
import com.google.android.exoplayer2.text.TextOutput
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import java.util.*

object ExoPlayerRecycler {
    private val logger = Logger("ExoPlayerRecycler")

    private val handler by lazy {
        Handler(Looper.getMainLooper())
    }

    private var cached: SimpleExoPlayer? = null

    private val releaseCached = Runnable {
        if (this.cached != null) {
            logger.time("Release cached player") {
                cached?.release()
            }

            cached = null
        }
    }

    fun release(exo: SimpleExoPlayer) {
        // release previously cached exo player
        this.cached?.let { cached ->
            logger.time("Released previously cached player") {
                cached.release()
            }
        }

        logger.debug { "Schedule player for delayed release" }
        this.cached = exo

        // schedule a release of the exo player in a moment while
        handler.removeCallbacks(releaseCached)
        handler.postDelayed(releaseCached, 2500)
    }

    fun get(context: Context): SimpleExoPlayer {
        val exo = cached ?: return newExoPlayer(context).also { exo ->
            // keeps limited resources after calling .stop()
            exo.setForegroundMode(true)
        }

        logger.debug { "Got cached exo player for reuse" }

        this.cached = null
        return exo
    }

    private fun newExoPlayer(context: Context): SimpleExoPlayer {
        val ctx = context.applicationContext

        logger.debug { "Create new exo player" }
        return SimpleExoPlayer
                .Builder(ctx, RenderersFactory(ctx), Mp4Extractor.FACTORY)
                .build()
    }

    private class RenderersFactory(ctx: Context) : DefaultRenderersFactory(ctx) {
        override fun buildCameraMotionRenderers(context: Context, extensionRendererMode: Int, out: ArrayList<Renderer>) {
        }

        override fun buildMetadataRenderers(context: Context, output: MetadataOutput, outputLooper: Looper, extensionRendererMode: Int, out: ArrayList<Renderer>) {
        }

        override fun buildMiscellaneousRenderers(context: Context, eventHandler: Handler, extensionRendererMode: Int, out: ArrayList<Renderer>) {
        }

        override fun buildTextRenderers(context: Context, output: TextOutput, outputLooper: Looper, extensionRendererMode: Int, out: ArrayList<Renderer>) {
        }
    }
}

