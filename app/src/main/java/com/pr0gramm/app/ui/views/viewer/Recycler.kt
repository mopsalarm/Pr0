package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time

@OptIn(UnstableApi::class)
object ExoPlayerRecycler {
    private val logger = Logger("ExoPlayerRecycler")

    private val handler by lazy {
        Handler(Looper.getMainLooper())
    }

    private var cached: ExoPlayer? = null

    private val releaseCached = Runnable {
        if (this.cached != null) {
            logger.time("Release cached player") {
                cached?.release()
            }

            cached = null
        }
    }

    fun release(exo: ExoPlayer) {
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

    fun get(context: Context): ExoPlayer {
        val exo = cached ?: return newExoPlayer(context).also { exo ->
            // keeps limited resources after calling .stop()
            exo.setForegroundMode(true)
        }

        logger.debug { "Got cached exo player for reuse" }

        this.cached = null
        return exo
    }

    private fun newExoPlayer(context: Context): ExoPlayer {
        val ctx = context.applicationContext

        logger.debug { "Create new exo player" }
        return ExoPlayer
            .Builder(ctx, RenderersFactory(ctx))
            .build()
    }

    private class RenderersFactory(ctx: Context) : DefaultRenderersFactory(ctx) {
        override fun buildCameraMotionRenderers(
            context: Context,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
        }

        override fun buildMetadataRenderers(
            context: Context,
            output: MetadataOutput,
            outputLooper: Looper,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
        }

        override fun buildMiscellaneousRenderers(
            context: Context,
            eventHandler: Handler,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
        }

        override fun buildTextRenderers(
            context: Context,
            output: TextOutput,
            outputLooper: Looper,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
        }
    }
}

