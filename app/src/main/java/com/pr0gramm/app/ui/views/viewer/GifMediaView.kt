package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.widget.ImageView
import androidx.core.view.isVisible
import com.pr0gramm.app.Duration
import com.pr0gramm.app.R
import com.pr0gramm.app.services.GifDrawableLoader
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.util.addOnAttachListener
import com.pr0gramm.app.util.addOnDetachListener
import com.pr0gramm.app.util.checkMainThread
import kotterknife.bindView
import pl.droidsonroids.gif.GifDrawable
import rx.functions.Action1

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

        loadGif()

        // reload on attach
        addOnAttachListener {
            loadGif()
        }

        // cleanup on detach!
        addOnDetachListener {
            imageView.setImageDrawable(null)
            gif?.recycle()
            gif = null
        }
    }

    private fun loadGif() {
        if (gif != null) {
            return
        }

        showBusyIndicator()

        gifDrawableLoader.load(effectiveUri)
                .compose(backgroundBindView())
                .doAfterTerminate { this.hideBusyIndicator() }
                .subscribe(Action1 { this.onDownloadStatus(it) }, defaultOnError())
    }

    private fun onDownloadStatus(state: GifDrawableLoader.Status) {
        checkMainThread()

        onDownloadProgress(state.progress)

        if (state.finished) {
            gif = state.drawable?.also { gif ->
                imageView.setImageDrawable(gif)

                viewAspect = gif.intrinsicWidth.toFloat() / gif.intrinsicHeight

                if (isPlaying) {
                    imageView.animate().alpha(1f)
                            .withEndAction { onMediaShown() }
                            .setDuration(MediaView.ANIMATION_DURATION)
                            .start()
                } else {
                    imageView.alpha = 1f
                    gif.stop()
                }
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
