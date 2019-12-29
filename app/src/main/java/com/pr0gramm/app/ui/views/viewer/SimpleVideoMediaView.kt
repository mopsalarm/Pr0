package com.pr0gramm.app.ui.views.viewer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.VideoListener
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.ui.views.viewer.video.InputStreamCacheDataSource
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

    init {
        logger.debug { "Using simple exo player to play videos." }

        if (config.audio) {
            val muteView = LayoutInflater
                    .from(context)
                    .inflate(R.layout.player_mute_view, this, false) as ImageView

            // controller will handle the button clicks & stuff
            volumeController = VolumeController(muteView) { exo }

            // show the mute button in the post view
            publishControllerView(muteView)
        } else {
            volumeController = null
        }

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

//        videoPlayer.buffering()
//                .sample(500, TimeUnit.MILLISECONDS, MainThreadScheduler)
//                .observeOn(MainThreadScheduler)
//                .compose(RxLifecycleAndroid.bindView<Boolean>(this))
//                .subscribe { this.showBusyIndicator(it) }

        // videoPlayer.detaches.subscribe { storePlaybackPosition() }

        // this.videoPlayer = DebugVideoPlayer(videoPlayer)
        // restorePreviousSeek()
    }

    override fun currentVideoProgress(): ProgressInfo? {
        val duration = exo?.contentDuration?.takeIf { it > 0 } ?: return null
        val position = exo?.currentPosition?.takeIf { it >= 0 } ?: return null
        val buffered = exo?.contentBufferedPosition?.takeIf { it >= 0 } ?: return null

        return ProgressInfo(position.toFloat() / duration, buffered.toFloat() / duration)
    }

    private fun play() {
        logger.info { "$effectiveUri, ${exo == null}, $isPlaying" }
        if (exo != null || !isPlaying) {
            return
        }

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

        exo = ExoPlayerFactory.newSimpleInstance(context).apply {
            setVideoTextureView(find(R.id.texture_view))
            prepare(mediaSource, false, false)
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            volume = 0f

            addVideoListener(videoListener)

            SeekController.restore(config.mediaUri.id, this)
        }

        // apply volume to the exo player if needed.
        volumeController?.applyMuteState()
    }

    private fun stop() {
        this.exo?.let { exo ->
            logger.info { "Stopping exo for $effectiveUri" }

            // store position so we can restore it later
            SeekController.store(config.mediaUri.id, exo)

            // kill the player
            exo.release()

            this.exo = null
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
            exo.seekTo((exo.duration * fraction).toLong())
        }
    }

    override fun onSeekbarVisibilityChanged(show: Boolean) {
        volumeController?.view?.let { muteView ->
            muteView.animate().cancel()

            if (show) {
                muteView.animate()
                        .alpha(0f)
                        .translationY(muteView.height.toFloat())
                        .withEndAction { muteView.isVisible = false }
                        .setInterpolator(AccelerateInterpolator())
                        .start()

            } else {
                muteView.alpha = 0f
                muteView.visibility = View.VISIBLE
                muteView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setListener(null)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
            }
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

    private val videoListener = object : VideoListener {
        override fun onRenderedFirstFrame() {
            if (isPlaying) {
                updateTimeline()
                onMediaShown()
            }
        }
    }

    companion object {
        private val logger = Logger("VideoMediaView")

    }
}

