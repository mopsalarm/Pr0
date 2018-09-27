package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v4.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.jakewharton.rxbinding.view.detaches
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.ui.views.viewer.video.AndroidVideoPlayer
import com.pr0gramm.app.ui.views.viewer.video.ExoVideoPlayer
import com.pr0gramm.app.ui.views.viewer.video.RxVideoPlayer
import com.pr0gramm.app.ui.views.viewer.video.VideoPlayer
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.edit
import com.pr0gramm.app.util.hideViewEndAction
import com.pr0gramm.app.util.logger
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import kotterknife.bindView
import org.kodein.di.erased.instance
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

@SuppressLint("ViewConstructor")
class VideoMediaView(config: MediaView.Config) : AbstractProgressMediaView(config, R.layout.player_kind_video), VideoPlayer.Callbacks {
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // the video player that does all the magic
    private val videoPlayer: RxVideoPlayer
    private val muteButtonView: ImageView

    private val videoPlayerParent: AspectLayout by bindView(R.id.video_container)

    private val settings: Settings by instance()
    private val proxyService: ProxyService by instance()
    private val preferences: SharedPreferences by instance()

    private var videoViewInitialized: Boolean = false
    private var errorShown: Boolean = false
    private var statsSent: Boolean = false
    private var droppedFramesShown: Boolean = false

    init {
        videoPlayer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && settings.useExoPlayer) {
            logger.info("Using exo player to play videos.")
            ExoVideoPlayer(context, config.audio, videoPlayerParent)
        } else {
            logger.info("Falling back on simple android video player.")
            AndroidVideoPlayer(context, videoPlayerParent)
        }

        muteButtonView = LayoutInflater
                .from(context)
                .inflate(R.layout.player_mute_view, this, false) as ImageView

        muteButtonView.visibility = if (hasAudio()) View.VISIBLE else View.GONE

        muteButtonView.setOnClickListener {
            setMuted(!videoPlayer.muted)
        }

        detaches().subscribe { videoPlayer.videoCallbacks = null }

        videoPlayer.buffering()
                .sample(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycleAndroid.bindView<Boolean>(this))
                .subscribe { this.showBusyIndicator(it) }


        videoPlayer.detaches().subscribe { storePlaybackPosition() }

        restorePreviousSeek()

        publishControllerView(muteButtonView)
    }

    private fun restorePreviousSeek() {
        // restore seek position if known
        val seekTo = seekToCache.get(config.mediaUri.id)
        if (seekTo != null && seekTo.valid) {
            logger.info("Restoring playback position {}", seekTo)
            videoPlayer.seekTo(seekTo.time)
        }
    }

    override fun userSeekable(): Boolean {
        return true
    }

    override fun playMedia() {
        super.playMedia()

        // apply state before starting playback.
        applyMuteState()

        if (!videoViewInitialized) {
            showBusyIndicator()

            videoViewInitialized = true

            videoPlayer.videoCallbacks = this

            if (mediaUri.isLocal || videoPlayer is ExoVideoPlayer) {
                videoPlayer.open(effectiveUri)
            } else {
                // for the old player, we need to proxy the url to improve caching.
                videoPlayer.open(proxyService.proxy(effectiveUri))
            }
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
                    .setListener(hideViewEndAction(muteButtonView))
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

    private fun setMuted(wantMuted: Boolean) {
        var muted = wantMuted

        if (muted) {
            audioManager.abandonAudioFocus(afChangeListener)
        } else {
            val result = audioManager.requestAudioFocus(afChangeListener,
                    AudioManager.STREAM_MUSIC, audioFocusDurationHint)

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                logger.info("Did not get audio focus, muting now!")
                muted = true
            }
        }

        logger.info("Setting mute state on video player: {}", muted)
        videoPlayer.muted = muted

        val icon: Drawable = if (muted) {
            storeUnmuteTime(0)

            ContextCompat.getDrawable(context, R.drawable.ic_volume_off_white_24dp)!!
        } else {
            storeUnmuteTime(System.currentTimeMillis())

            AndroidUtility.getTintentDrawable(context,
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

    override val videoProgress: AbstractProgressMediaView.ProgressInfo? get() {
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
            Stats.get().incrementCounter("video.playback.succeeded")
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

    fun storePlaybackPosition() {
        val currentPosition = videoPlayer.currentPosition
        seekToCache.put(config.mediaUri.id, ExpiringTimestamp(currentPosition))
        logger.info("Stored current position {}", currentPosition)
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
            Stats.get().incrementCounter("video.playback.failed")
            statsSent = true
        }
    }

    override fun onDroppedFrames(count: Int) {
        if (!droppedFramesShown) {
            showDialog(context) {
                dontShowAgainKey("VideoMediaView.dropped-frames")
                content(R.string.media_dropped_frames_hint)
                positive()
            }

            droppedFramesShown = true
        }
    }

    override fun userSeekTo(fraction: Float) {
        logger.info("User wants to seek to position {}", fraction)
        videoPlayer.seekTo((fraction * videoPlayer.duration).toInt())
    }

    private val afChangeListener = object : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AUDIOFOCUS_LOSS) {
                audioManager.abandonAudioFocus(this)
                logger.info("Lost audio focus, muting now.")
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
        private val logger = logger("VideoMediaView")

        private val seekToCache = LruCache<Long, ExpiringTimestamp>(16)

        private const val KEY_LAST_UNMUTED_VIDEO = "VideoMediaView.lastUnmutedVideo"
    }
}
