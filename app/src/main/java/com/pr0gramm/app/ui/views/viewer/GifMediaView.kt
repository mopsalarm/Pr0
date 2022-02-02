package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import androidx.core.view.isVisible
import com.pr0gramm.app.Duration
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.PlayerGifBinding
import com.pr0gramm.app.services.GifDrawableLoader
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.util.addOnDetachListener
import com.pr0gramm.app.util.checkMainThread
import pl.droidsonroids.gif.GifDrawable

/**
 */
@SuppressLint("ViewConstructor")
class GifMediaView(config: Config) : AbstractProgressMediaView(config, R.layout.player_gif) {
    private val gifDrawableLoader: GifDrawableLoader by instance()

    private val views = PlayerGifBinding.bind(this)

    // the gif that is shown
    private var gif: GifDrawable? = null

    init {
        views.image.alpha = 0f

        onAttachedScope {
            if (gif == null) {
                loadGif()
            }
        }

        // cleanup on detach!
        addOnDetachListener {
            views.image.setImageDrawable(null)

            // recycle if possible
            gif?.recycle()
            gif = null
        }
    }

    private suspend fun loadGif() {
        showBusyIndicator()

        try {
            gifDrawableLoader.load(effectiveUri).collect { state ->
                onDownloadStatus(state)
            }
        } finally {
            hideBusyIndicator()
        }
    }

    private fun onDownloadStatus(state: GifDrawableLoader.State) {
        checkMainThread()

        onDownloadProgress(state.progress)

        if (state.drawable != null && isAttachedToWindow) {
            this.gif = state.drawable

            views.image.setImageDrawable(state.drawable)

            viewAspect = state.drawable.intrinsicWidth.toFloat() / state.drawable.intrinsicHeight

            if (isPlaying) {
                gif?.start()

                views.image.animate().alpha(1f)
                    .withEndAction { onMediaShown() }
                    .setDuration(MediaView.ANIMATION_DURATION)
                    .start()
            } else {
                views.image.alpha = 1f
                state.drawable.stop()
            }
        }
    }

    private fun onDownloadProgress(progress: Float) {
        checkMainThread()
        (busyIndicator as? BusyIndicator)?.progress = progress
    }

    override fun onPreviewRemoved() {
        views.image.isVisible = true
    }

    override fun currentVideoProgress(): ProgressInfo? {
        val gif = gif

        if (isPlaying && gif != null) {
            val position = gif.currentFrameIndex
            val duration = gif.numberOfFrames

            if (position >= 0 && duration > 0) {
                return ProgressInfo(
                    position / duration.toFloat(), 1f,
                    duration = Duration.millis(gif.duration.toLong())
                )
            }
        }

        return null
    }

    override fun playMedia() {
        super.playMedia()

        gif.takeIf { isPlaying }?.let {
            gif?.start()
            onMediaShown()
        }
    }

    override fun stopMedia() {
        super.stopMedia()
        gif?.stop()
    }

    override fun rewind() {
        this.gif?.seekTo(0)
    }
}


