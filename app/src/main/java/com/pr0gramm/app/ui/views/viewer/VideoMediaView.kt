package com.pr0gramm.app.ui.views.viewer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.collection.LruCache
import androidx.core.animation.doOnEnd
import androidx.core.content.edit
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.ui.views.viewer.video.ExoVideoPlayer
import com.pr0gramm.app.ui.views.viewer.video.RxVideoPlayer
import com.pr0gramm.app.ui.views.viewer.video.VideoPlayer
import com.pr0gramm.app.util.*
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import kotterknife.bindView
import java.util.concurrent.TimeUnit

@SuppressLint("ViewConstructor")
class VideoMediaView(config: MediaView.Config) : AbstractProgressMediaView(config, R.layout.player_kind_video), VideoPlayer.Callbacks {
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // the video player that does all the magic
    private val videoPlayer: RxVideoPlayer
    private val muteButtonView: ImageView

    private val videoPlayerParent: AspectLayout by bindView(R.id.video_container)

    private val settings: Settings by instance()
    private val preferences: SharedPreferences by instance()

    private var videoViewInitialized: Boolean = false
    private var errorShown: Boolean = false
    private var statsSent: Boolean = false

    init {
        logger.debug { "Using exo player to play videos." }
        videoPlayer = ExoVideoPlayer(context, config.audio, videoPlayerParent)

        muteButtonView = LayoutInflater
                .from(context)
                .inflate(R.layout.player_mute_view, this, false) as ImageView

        muteButtonView.visibility = if (hasAudio()) View.VISIBLE else View.GONE

        muteButtonView.setOnClickListener {
            setMuted(!videoPlayer.muted)
        }

        addOnDetachListener { videoPlayer.videoCallbacks = null }

        videoPlayer.buffering()
                .sample(500, TimeUnit.MILLISECONDS, MainThreadScheduler)
                .observeOn(MainThreadScheduler)
                .compose(RxLifecycleAndroid.bindView<Boolean>(this))
                .subscribe { this.showBusyIndicator(it) }


        videoPlayer.detaches.subscribe { storePlaybackPosition() }

        restorePreviousSeek()

        publishControllerView(muteButtonView)
    }

    private fun restorePreviousSeek() {
        // restore seek position if known
        val seekTo = seekToCache.get(config.mediaUri.id)
        if (seekTo != null && seekTo.valid) {
            logger.info { "Restoring playback position $seekTo" }
            videoPlayer.seekTo(seekTo.time)
        }
    }

    override fun userSeekable(): Boolean {
        return true
    }

    override fun onDoubleTap(event: MotionEvent): Boolean {
        if (userSeekable()) {
            val tapPosition = event.x / width

            // always seek 10 seconds or 25%, whatever is less
            val skipFraction = (10000 / videoPlayer.duration.toFloat()).coerceAtMost(0.25f)

            if (tapPosition < 0.25) {
                userSeekTo(videoPlayer.progress - skipFraction)
                animateMediaControls(find(R.id.rewind), direction = -1)
                return true

            } else if (tapPosition > 0.75) {
                userSeekTo(videoPlayer.progress + skipFraction)
                animateMediaControls(find(R.id.fast_forward), direction = +1)
                return true
            }
        }

        return super.onDoubleTap(event)
    }

    private fun animateMediaControls(imageView: ImageView, direction: Int) {
        imageView.visible = true

        val xTrans = imageView.width * 0.25f * direction
        ObjectAnimator.ofPropertyValuesHolder(imageView,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.7f, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -xTrans, xTrans)).apply {

            duration = 300
            interpolator = AccelerateDecelerateInterpolator()

            doOnEnd { imageView.visible = false }

            start()
        }
    }

    override fun playMedia() {
        super.playMedia()

        // apply state before starting playback.
        applyMuteState()

        if (!videoViewInitialized) {
            showBusyIndicator()

            videoViewInitialized = true

            videoPlayer.videoCallbacks = this

            videoPlayer.open(effectiveUri)
        }

        // restore seek position if known
        restorePreviousSeek()

        videoPlayer.start()
    }

    override fun onSeekbarVisibilityChanged(show: Boolean) {
        // do not touch the button, if we dont have audio at all.
        if (!hasAudio())
            return

        muteButtonView.animate().cancel()

        if (show) {
            muteButtonView.animate()
                    .alpha(0f)
                    .translationY(muteButtonView.height.toFloat())
                    .withEndAction { muteButtonView.visible = false }
                    .setInterpolator(AccelerateInterpolator())
                    .start()
        } else {
            muteButtonView.alpha = 0f
            muteButtonView.visibility = View.VISIBLE
            muteButtonView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setListener(null)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
        }
    }

    /**
     * Mute if not "unmuted" within the last 10 minutes.
     */
    private fun applyMuteState() {
        if (hasAudio()) {
            val now = System.currentTimeMillis()
            val lastUnmutedVideo = preferences.getLong(KEY_LAST_UNMUTED_VIDEO, 0)
            val diff = (now - lastUnmutedVideo) / 1000
            setMuted(diff > 10 * 60)
        } else {
            videoPlayer.muted = true
        }
    }

    private fun storeUnmuteTime(time: Long) {
        preferences.edit {
            putLong(KEY_LAST_UNMUTED_VIDEO, time)
        }
    }

    private fun setMuted(wantMuted: Boolean): Unit = catchAll {
        var muted = wantMuted

        if (muted) {
            audioManager.abandonAudioFocus(afChangeListener)
        } else {
            val result = audioManager.requestAudioFocus(afChangeListener,
                    AudioManager.STREAM_MUSIC, audioFocusDurationHint)

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                logger.info { "Did not get audio focus, muting now!" }
                muted = true
            }
        }

        logger.info { "Setting mute state on video player: $muted" }
        videoPlayer.muted = muted

        val icon: Drawable = if (muted) {
            storeUnmuteTime(0)

            AppCompatResources.getDrawable(context, R.drawable.ic_volume_off_white_24dp)!!
        } else {
            storeUnmuteTime(System.currentTimeMillis())

            AndroidUtility.getTintedDrawable(context,
                    R.drawable.ic_volume_up_white_24dp, ThemeHelper.accentColor)
        }

        muteButtonView.setImageDrawable(icon)
    }

    private val audioFocusDurationHint: Int
        get(): Int {
            return if (settings.audioFocusTransient) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            } else {
                AudioManager.AUDIOFOCUS_GAIN
            }
        }

    override fun currentVideoProgress(): AbstractProgressMediaView.ProgressInfo? {
        if (videoViewInitialized && isPlaying) {
            return AbstractProgressMediaView.ProgressInfo(videoPlayer.progress, videoPlayer.buffered)
        }

        return null
    }

    override fun onVideoBufferingStarts() {}

    override fun onVideoBufferingEnds() {}

    override fun onVideoRenderingStarts() {
        if (isPlaying) {
            // mark media as viewed
            onMediaShown()
        }

        if (!statsSent) {
            Stats().incrementCounter("video.playback.succeeded")
            statsSent = true
        }
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        viewAspect = width / height.toFloat()
    }

    override fun stopMedia() {
        super.stopMedia()

        videoPlayer.pause()
        audioManager.abandonAudioFocus(afChangeListener)

        storePlaybackPosition()
    }

    private fun storePlaybackPosition() {
        val currentPosition = videoPlayer.currentPosition
        seekToCache.put(config.mediaUri.id, ExpiringTimestamp(currentPosition))
        logger.debug { "Stored current position $currentPosition" }
    }

    override fun rewind() {
        videoPlayer.rewind()
    }

    override fun onVideoError(message: String, kind: VideoPlayer.ErrorKind) {
        // we might be finished here :/
        hideBusyIndicator()

        if (!errorShown) {
            showDialog(context) {
                dontShowAgainKey("video." + message.hashCode())
                content(R.string.media_exo_error, message)
                positive()
            }

            errorShown = true
        }

        if (!statsSent && kind !== VideoPlayer.ErrorKind.NETWORK) {
            Stats().incrementCounter("video.playback.failed")
            statsSent = true
        }
    }

    override fun userSeekTo(fraction: Float) {
        logger.info { "User wants to seek to position $fraction" }
        videoPlayer.seekTo((fraction.coerceIn(0f, 1f) * videoPlayer.duration).toInt())
    }

    private val afChangeListener = object : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AUDIOFOCUS_LOSS) {
                audioManager.abandonAudioFocus(this)
                logger.info { "Lost audio focus, muting now." }
                setMuted(true)
            }
        }
    }

    /**
     * This timestamp value is only valid for 60 seconds.
     */
    private class ExpiringTimestamp(val time: Int) {
        val created: Long = System.currentTimeMillis()
        val valid: Boolean get() = (System.currentTimeMillis() - created) < 60 * 1000
    }

    companion object {
        private val logger = Logger("VideoMediaView")

        private val seekToCache = LruCache<Long, ExpiringTimestamp>(16)

        private const val KEY_LAST_UNMUTED_VIDEO = "VideoMediaView.lastUnmutedVideo"
    }
}
