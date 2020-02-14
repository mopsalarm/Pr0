package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.SimpleExoPlayer
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time

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
        logger.debug { "Create new exo player" }
        return SimpleExoPlayer
                .Builder(context.applicationContext)
                .build()
    }
}

