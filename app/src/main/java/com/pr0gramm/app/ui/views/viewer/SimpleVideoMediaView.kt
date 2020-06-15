package com.pr0gramm.app.ui.views.viewer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.VideoListener
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.views.viewer.video.InputStreamCacheDataSource
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.addOnAttachListener
import com.pr0gramm.app.util.addOnDetachListener
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.find


@SuppressLint("ViewConstructor")
class SimpleVideoMediaView(config: Config) : AbstractProgressMediaView(config, R.layout.player_kind_simple_video) {
    private val logger = Logger("SimpleVideoMediaView(${config.mediaUri.id})")

    private val volumeController: VolumeController?

    // the current player.
    // Will be released on detach and re-created on attach.
    private var exo: SimpleExoPlayer? = null

    private val controlsView = LayoutInflater
            .from(context)
            .inflate(R.layout.player_video_controls, this, false) as ViewGroup

    init {
        if (config.audio) {
            val muteView: ImageView = controlsView.find(R.id.mute)

            // set visible, we need it.
            muteView.isVisible = true

            // controller will handle the button clicks & stuff
            volumeController = VolumeController(muteView) { exo }

        } else {
            volumeController = null
        }

        publishControllerView(controlsView)

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
            addOnAttachListener {
                if (isPlaying) {
                    logger.debug { "View is attached, re-create video player now." }
                    play()
                }
            }

            addOnDetachListener {
                if (exo != null) {
                    logger.debug { "View is detached, releasing video player now." }
                    stop()
                }
            }
        }

        controlsView.find<View>(R.id.pause).setOnClickListener {
            val exo = exo ?: return@setOnClickListener

            // toggle playbook
            exo.playWhenReady = !exo.playWhenReady

            updatePauseViewIcon()
        }
    }

    private fun updatePauseViewIcon() {
        val exo = this.exo ?: return

        val icon = if (exo.playWhenReady) R.drawable.ic_video_pause else R.drawable.ic_video_play
        controlsView.find<ImageView>(R.id.pause).setImageResource(icon)

        if (!exo.playWhenReady) {
            val dr = AndroidUtility.getTintedDrawable(context, R.drawable.ic_video_play, ThemeHelper.accentColor)
            controlsView.find<ImageView>(R.id.pause).setImageDrawable(dr)
        }
    }

    override fun currentVideoProgress(): ProgressInfo? {
        val duration = exo?.contentDuration?.takeIf { it > 0 } ?: return null
        val position = exo?.currentPosition?.takeIf { it >= 0 } ?: return null
        val buffered = exo?.contentBufferedPosition?.takeIf { it >= 0 } ?: return null

        return ProgressInfo(position.toFloat() / duration, buffered.toFloat() / duration,
                duration = Duration.millis(duration))
    }

    private fun play() {
        logger.info { "$effectiveUri, ${exo == null}, $isPlaying" }
        if (exo != null || !isPlaying) {
            return
        }

        showBusyIndicator()

        logger.info { "Starting exo for $effectiveUri" }


        val dataSourceFactory = DataSource.Factory {
            val cache = context.injector.instance<Cache>()
            InputStreamCacheDataSource(cache)
        }

        val extractorsFactory = ExtractorsFactory {
            arrayOf(FragmentedMp4Extractor(), Mp4Extractor())
        }

        val mediaSource = ProgressiveMediaSource
                .Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(effectiveUri)

        exo = ExoPlayerRecycler.get(context).apply {
            setVideoTextureView(find(R.id.texture_view))

            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            volume = 0f

            // don't forget to remove listeners in stop()
            addListener(playerListener)
            addVideoListener(videoListener)

            prepare(mediaSource, false, false)

            SeekController.restore(config.mediaUri.id, this)
        }

        // apply volume to the exo player if needed.
        volumeController?.applyMuteState()

        // update pause icon. The player got reset
        updatePauseViewIcon()
    }

    private fun stop() {
        this.exo?.let { exo ->
            logger.info { "Stopping exo for $effectiveUri" }

            // store position so we can restore it later
            SeekController.store(config.mediaUri.id, exo)

            // continue music if there is any, but give it some small delay, so
            // another video player could take over before starting the actual playback.
            volumeController?.abandonAudioFocusSoon()

            // reset the player now. No one else has access to it anymore.
            this.exo = null

            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
                exo.stop(true)

                exo.removeListener(playerListener)
                exo.removeVideoListener(videoListener)
                exo.setVideoTextureView(null)

                ExoPlayerRecycler.release(exo)
            } else {
                // on android 6 we just release the player, cause we got some crashes.
                // So, maybe this helps.
                exo.release()
            }
        }
    }

    override fun playMedia() {
        super.playMedia()
        play()
    }

    override fun stopMedia() {
        super.stopMedia()
        stop()
    }

    override fun rewind() {
        exo?.seekTo(0L)
    }

    override fun userSeekable(): Boolean {
        return true
    }

    override fun userSeekTo(fraction: Float) {
        this.exo?.let { exo ->
            exo.seekTo((exo.duration * fraction.coerceAtLeast(0f)).toLong())
        }
    }

    override fun onSeekbarVisibilityChanged(show: Boolean) {
        controlsView.animate().cancel()

        if (show) {
            controlsView.animate()
                    .alpha(0f)
                    .translationY(controlsView.height.toFloat())
                    .withEndAction { controlsView.isVisible = false }
                    .setInterpolator(AccelerateInterpolator())
                    .start()

        } else {
            controlsView.alpha = 0f
            controlsView.visibility = View.VISIBLE
            controlsView.animate()
                    .alpha(0.5f)
                    .translationY(0f)
                    .setListener(null)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
        }
    }

    override fun onDoubleTap(event: MotionEvent): Boolean {
        val exo = this.exo
        if (exo != null) {
            val tapPosition = event.x / width

            // always seek 10 seconds or 25%, whatever is less
            val skipFraction = 10_000L.coerceAtMost(exo.duration / 4)

            if (tapPosition < 0.25) {
                userSeekTo((exo.currentPosition - skipFraction) / exo.duration.toFloat())
                animateMediaControls(find(R.id.rewind), direction = -1)
                return true

            } else if (tapPosition > 0.75) {
                userSeekTo((exo.currentPosition + skipFraction) / exo.duration.toFloat())
                animateMediaControls(find(R.id.fast_forward), direction = +1)
                return true
            }
        }

        return super.onDoubleTap(event)
    }

    private fun animateMediaControls(imageView: ImageView, direction: Int) {
        imageView.isVisible = true

        val xTrans = imageView.width * 0.25f * direction
        ObjectAnimator.ofPropertyValuesHolder(imageView,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.7f, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -xTrans, xTrans)).apply {

            duration = 300
            interpolator = AccelerateDecelerateInterpolator()

            doOnEnd { imageView.isVisible = false }

            start()
        }
    }

    private val playerListener = object : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            showBusyIndicator(playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePauseViewIcon()
        }
    }

    private val videoListener = object : VideoListener {
        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            if (viewAspect < 0) {
                viewAspect = width.toFloat() / height.toFloat() * pixelWidthHeightRatio
            }
        }

        override fun onRenderedFirstFrame() {
            hideBusyIndicator()
            if (isPlaying) {
                updateTimeline()
                onMediaShown()
            }
        }
    }
}
