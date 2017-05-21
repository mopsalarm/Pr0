package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding.view.RxView
import com.pr0gramm.app.R
import com.pr0gramm.app.services.GifDrawableLoader
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.endAction
import kotterknife.bindView
import pl.droidsonroids.gif.GifDrawable
import rx.functions.Action1

/**
 */
@SuppressLint("ViewConstructor")
class GifMediaView(config: MediaView.Config) : AbstractProgressMediaView(config, R.layout.player_gif) {
    private val gifDrawableLoader: GifDrawableLoader = instance()

    private val imageView: ImageView by bindView(R.id.image)

    // the gif that is shown
    private var gif: GifDrawable? = null

    init {
        imageView.alpha = 0f
        loadGif()

        // cleanup on detach!
        RxView.detaches(this).subscribe {
            imageView.setImageDrawable(null)
            gif?.recycle()
            gif = null
        }
    }

    private fun loadGif() {
        showBusyIndicator()

        gifDrawableLoader.load(effectiveUri)
                .compose(backgroundBindView())
                .doAfterTerminate { this.hideBusyIndicator() }
                .subscribe(Action1 { this.onDownloadStatus(it) }, defaultOnError())
    }

    private fun onDownloadStatus(state: GifDrawableLoader.DownloadStatus) {
        checkMainThread()

        onDownloadProgress(state.progress)

        if (state.finished) {
            gif = state.drawable?.also { gif ->
                imageView.setImageDrawable(gif)

                viewAspect = gif.intrinsicWidth.toFloat() / gif.intrinsicHeight

                if (isPlaying) {
                    imageView.animate().alpha(1f)
                            .setListener(endAction { onMediaShown() })
                            .setDuration(MediaView.ANIMATION_DURATION.toLong())
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
        imageView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (gif != null && isPlaying) {
            gif!!.start()
            onMediaShown()
        }
    }

    override val videoProgress: AbstractProgressMediaView.ProgressInfo? get() {
        gif?.takeIf { isPlaying }?.let { gif ->
            val position = gif.currentFrameIndex
            val duration = gif.numberOfFrames

            if (position >= 0 && duration > 0) {
                return AbstractProgressMediaView.ProgressInfo(position / duration.toFloat(), 1f)
            }
        }

        return null
    }

    override fun onPause() {
        super.onPause()
        if (gif != null && isPlaying)
            gif!!.pause()
    }

    override fun playMedia() {
        super.playMedia()
        if (gif != null && isPlaying) {
            gif!!.start()
            onMediaShown()
        }
    }

    override fun stopMedia() {
        super.stopMedia()
        if (gif != null)
            gif!!.stop()
    }

    override fun rewind() {
        if (gif != null && isPlaying) {
            gif!!.seekTo(0)
        }
    }
}
