package com.pr0gramm.app.ui.views.viewer

import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.SeekBar
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.hideViewEndAction
import org.slf4j.LoggerFactory

/**
 */
@Suppress("LeakingThis")
abstract class AbstractProgressMediaView(config: MediaView.Config, @LayoutRes layoutId: Int?) : MediaView(config, layoutId) {
    internal var progressTouched = false
    internal var lastUserInteraction: Long = -1

    private var progressEnabled = true
    private var firstTimeProgressValue = true

    private val seekBarView: SeekBar = LayoutInflater.from(context)
            .inflate(R.layout.player_video_seekbar, this, false) as SeekBar

    private val progressView: ProgressBar = LayoutInflater.from(context)
            .inflate(R.layout.player_video_progress, this, false) as ProgressBar

    protected abstract val videoProgress: ProgressInfo?

    init {
        publishControllerView(progressView)
        publishControllerView(seekBarView)

        updateTimeline()

        seekBarView.setOnSeekBarChangeListener(SeekbarChangeListener())
    }

    override fun onResume() {
        super.onResume()
        updateTimeline()
    }

    override fun playMedia() {
        super.playMedia()
        updateTimeline()
    }

    override fun onSingleTap(event: MotionEvent): Boolean {
        if (userSeekable()) {
            if (seekCurrentlyVisible()) {
                logger.info("Hide seekbar after tap.")

                lastUserInteraction = -1
                showSeekbar(false)
            } else {
                logger.info("Show seekbar after tap.")

                lastUserInteraction = System.currentTimeMillis()
                showSeekbar(true)
            }

            return true
        }

        return super.onSingleTap(event)
    }

    protected open fun userSeekable(): Boolean {
        return false
    }

    private fun seekCurrentlyVisible(): Boolean {
        return seekBarView.visibility == View.VISIBLE
    }

    private fun showSeekbar(show: Boolean) {
        val deltaY = AndroidUtility.dp(context, 12)

        val viewToShow = if (show) seekBarView else progressView
        val viewToHide = if (show) progressView else seekBarView

        if (viewToHide.visibility == View.VISIBLE) {
            viewToHide.translationY = 0f
            viewToHide.animate()
                    .alpha(0f)
                    .translationY(deltaY.toFloat())
                    .setListener(hideViewEndAction(viewToHide))
                    .setInterpolator(AccelerateInterpolator())
                    .start()

        }

        if (viewToShow.visibility != View.VISIBLE) {
            if (progressEnabled || progressView !== viewToShow) {
                viewToShow.alpha = 0f
                viewToShow.translationY = deltaY.toFloat()
                viewToShow.visibility = View.VISIBLE
                viewToShow.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setListener(null)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
            }

            onSeekbarVisibilityChanged(show)
        }

        if (show) {
            updateTimeline()
        }
    }

    protected open fun onSeekbarVisibilityChanged(show: Boolean) {
        // can be implemented in a subclass.
    }

    private fun updateTimeline() {
        if (!isPlaying)
            return

        if (!progressTouched) {
            val info = videoProgress
            if (info != null && shouldShowView(info)) {
                if (firstTimeProgressValue && progressEnabled) {
                    firstTimeProgressValue = false
                    progressView.visibility = View.VISIBLE
                    progressView.alpha = 1f
                    progressView.translationY = 0f
                }

                for (view in arrayOf(progressView, seekBarView)) {
                    view.max = 1000
                    view.progress = (1000 * info.progress).toInt()
                    view.secondaryProgress = (1000 * info.buffered).toInt()
                }

                if (userSeekable() && seekHideTimeoutReached()) {
                    logger.info("Hiding seekbar after idle timeout")
                    lastUserInteraction = -1
                    showSeekbar(false)
                }
            } else {
                lastUserInteraction = -1
                firstTimeProgressValue = true
                seekBarView.visibility = View.GONE
                progressView.visibility = View.GONE
            }
        }

        if (progressEnabled || seekCurrentlyVisible()) {
            removeCallbacks { this.updateTimeline() }
            postDelayed({ this.updateTimeline() }, 200)
        }
    }

    private fun seekHideTimeoutReached(): Boolean {
        return seekCurrentlyVisible()
                && lastUserInteraction > 0
                && System.currentTimeMillis() - lastUserInteraction > 3000
    }

    private fun shouldShowView(info: ProgressInfo): Boolean {
        return (progressEnabled || seekCurrentlyVisible()) && (info.progress >= 0 && info.progress <= 1 || info.buffered >= 0 && info.buffered <= 1)
    }

    /**
     * Implement to seek after user input.
     */
    protected open fun userSeekTo(fraction: Float) {
    }

    /**
     * Disable the little progressbar. We still allow seeking, if the user
     * touches the screen.
     */
    fun hideVideoProgress() {
        progressEnabled = false

        lastUserInteraction = -1
        firstTimeProgressValue = true
        seekBarView.visibility = View.GONE
        progressView.visibility = View.GONE
    }

    class ProgressInfo(val progress: Float, val buffered: Float)

    private inner class SeekbarChangeListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                lastUserInteraction = System.currentTimeMillis()
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            progressTouched = true
            lastUserInteraction = System.currentTimeMillis()
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            val currentValue = seekBar.progress
            userSeekTo(currentValue / seekBar.max.toFloat())

            progressTouched = false
            lastUserInteraction = System.currentTimeMillis()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("AbstractProgressMediaView")
    }
}
