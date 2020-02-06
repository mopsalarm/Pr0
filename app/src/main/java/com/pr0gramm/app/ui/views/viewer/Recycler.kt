package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.SimpleExoPlayer
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time


object ExoPlayerRecycler {
    private val recycler = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
        RecyclerNoop()
    } else {
        Recycler24()
    }

    fun release(exo: SimpleExoPlayer) = recycler.release(exo)
    fun get(context: Context): SimpleExoPlayer = recycler.get(context)

    abstract class Recycler {
        protected val logger = Logger("ExoPlayerHolder")

        abstract fun release(exo: SimpleExoPlayer)

        abstract fun get(context: Context): SimpleExoPlayer

        protected fun newExoPlayer(context: Context): SimpleExoPlayer {
            logger.debug { "Create new exo player" }
            val exo = SimpleExoPlayer
                    .Builder(context.applicationContext)
                    .build()

            return exo
        }
    }

    private class RecyclerNoop : Recycler() {
        override fun release(exo: SimpleExoPlayer) {
            exo.release()
        }

        override fun get(context: Context): SimpleExoPlayer {
            return newExoPlayer(context)
        }
    }

    private class Recycler24 : Recycler() {
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

        override fun release(exo: SimpleExoPlayer) {
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

        override fun get(context: Context): SimpleExoPlayer {
            val exo = cached ?: return newExoPlayer(context).also { exo ->
                // keeps limited resources after calling .stop()
                exo.setForegroundMode(true)
            }

            logger.debug { "Got cached exo player for reuse" }

            this.cached = null
            return exo
        }
    }

}
