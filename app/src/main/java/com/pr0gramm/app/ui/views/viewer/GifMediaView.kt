package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.widget.ImageView
import androidx.core.view.isVisible
import com.pr0gramm.app.Duration
import com.pr0gramm.app.R
import com.pr0gramm.app.services.GifDrawableLoader
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.util.addOnDetachListener
import com.pr0gramm.app.util.checkMainThread
import kotlinx.coroutines.flow.collect
import kotterknife.bindView
import pl.droidsonroids.gif.GifDrawable

/**
 */
@SuppressLint("ViewConstructor")
class GifMediaView(config: Config) : AbstractProgressMediaView(config, R.layout.player_gif) {
    private val gifDrawableLoader: GifDrawableLoader by instance()

    private val imageView: ImageView by bindView(R.id.image)

    // the gif that is shown
    private var gif: GifDrawable? = null

    init {
        imageView.alpha = 0f

        onAttachedScope {
            if (gif == null) {
                loadGif()
            }
        }

        // cleanup on detach!
        addOnDetachListener {
            imageView.setImageDrawable(null)

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

            imageView.setImageDrawable(state.drawable)

            viewAspect = state.drawable.intrinsicWidth.toFloat() / state.drawable.intrinsicHeight

            if (isPlaying) {
                imageView.animate().alpha(1f)
                        .withEndAction { onMediaShown() }
                        .setDuration(MediaView.ANIMATION_DURATION)
                        .start()
            } else {
                imageView.alpha = 1f
                state.drawable.stop()
            }
        }
    }

    private fun onDownloadProgress(progress: Float) {
        checkMainThread()
        (busyIndicator as? BusyIndicator)?.progress = progress
    }

    override fun onPreviewRemoved() {
        imageView.isVisible = true
    }

    override fun currentVideoProgress(): ProgressInfo? {
        gif?.takeIf { isPlaying }?.let { gif ->
            val position = gif.currentFrameIndex
            val duration = gif.numberOfFrames

            if (position >= 0 && duration > 0) {
                return ProgressInfo(position / duration.toFloat(), 1f,
                        duration = Duration.millis(gif.duration.toLong()))
            }
        }

        return null
    }

    override fun playMedia() {
        super.playMedia()

        gif.takeIf { isPlaying }?.let { gif ->
            gif.start()
            onMediaShown()
        }
    }

    override fun stopMedia() {
        super.stopMedia()
        gif?.stop()
    }

    override fun rewind() {
        gif?.seekTo(0)
    }
}
