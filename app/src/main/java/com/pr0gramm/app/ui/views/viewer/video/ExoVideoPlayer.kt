package com.pr0gramm.app.ui.views.viewer.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.FixedTrackSelection
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener
import com.jakewharton.rxbinding.view.RxView
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.util.*
import org.kodein.di.erased.instance

/**
 * Stripped down version of [android.widget.VideoView].
 */
class ExoVideoPlayer(context: Context, hasAudio: Boolean, parentView: AspectLayout) :
        RxVideoPlayer(), VideoPlayer, Player.EventListener {


    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val settings = Settings.get()

    private val exo: ExoPlayer
    private val exoVideoRenderer: MediaCodecVideoRenderer

    private var exoAudioRenderer: MediaCodecAudioRenderer? = null

    override var muted: Boolean by observeChange(false) { applyVolumeState() }

    private val surfaceProvider: ViewBackend

    private var uri: Uri? = null
    private var initialized: Boolean = false

    private val backendViewCallbacks = object : ViewBackend.Callbacks {
        override fun onAvailable(backend: ViewBackend) {
            sendSetSurfaceMessage(true, backend.currentSurface)
        }

        override fun onSizeChanged(backend: ViewBackend, width: Int, height: Int) {}

        override fun onDestroy(backend: ViewBackend) {
            sendSetSurfaceMessage(true, null)
        }
    }

    init {
        // Use a texture view to display the video.
        surfaceProvider = if (settings.useTextureView) {
            TextureViewBackend(context, backendViewCallbacks)
        } else {
            SurfaceViewBackend(context, backendViewCallbacks)
        }

        val videoView = surfaceProvider.view
        parentView.addView(videoView)

        logger.info { "Create ExoPlayer instance" }

        val videoListener = VideoListener(callbacks, parentView)

        val mediaCodecSelector = MediaCodecSelectorImpl(settings)
        exoVideoRenderer = MediaCodecVideoRenderer(context, mediaCodecSelector,
                5000, handler, videoListener, MAX_DROPPED_FRAMES)

        val renderers = if (hasAudio) {
            exoAudioRenderer = MediaCodecAudioRenderer(context, mediaCodecSelector)
            arrayOf(exoVideoRenderer, exoAudioRenderer!!)
        } else {
            arrayOf(exoVideoRenderer)
        }

        exo = ExoPlayerFactory.newInstance(renderers, DefaultTrackSelector(FixedTrackSelection.Factory()))
        exo.addListener(this)

        RxView.detaches(videoView).subscribe {
            detaches.onNext(Unit)

            pause()

            logger.info { "Detaching view, releasing exo player now." }
            exo.removeListener(this)
            exo.release()
        }
    }

    override fun open(uri: Uri) {
        logger.info { "Opening exo player for uri $uri" }
        this.uri = uri
    }

    override fun start() {
        val uri = uri
        if (initialized || uri == null)
            return

        initialized = true

        val extractorsFactory = ExtractorsFactory { arrayOf(FragmentedMp4Extractor(), Mp4Extractor()) }

        val mediaSource = ExtractorMediaSource.Factory(DataSourceFactory(context))
                .setExtractorsFactory(extractorsFactory)
                .createMediaSource(uri)

        // apply volume before starting the player
        applyVolumeState()

        logger.info { "Preparing exo player now'" }

        exo.prepare(mediaSource, false, false)
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.playWhenReady = true

        applyVolumeState()

        // initialize the renderer with a surface, if we already have one.
        // this might be the case, if we are restarting the video after
        // a call to pause.
        if (surfaceProvider.hasSurface) {
            sendSetSurfaceMessage(true, surfaceProvider.currentSurface)
        }
    }

    override val progress: Float get() {
        val duration = exo.duration.toFloat()
        return if (duration > 0) exo.currentPosition / duration else -1f
    }

    override val buffered: Float get() {
        var buffered = exo.bufferedPercentage / 100f
        if (buffered == 0f) {
            buffered = -1f
        }

        return buffered
    }

    override val currentPosition: Int get() {
        return exo.currentPosition.toInt()
    }

    override val duration: Int get() {
        return exo.duration.toInt()
    }

    override fun pause() {
        logger.info { "Stopping exo player now" }
        sendSetSurfaceMessage(false, null)
        exo.stop()
        initialized = false
    }

    internal fun sendSetSurfaceMessage(async: Boolean, surface: Surface?) {
        val message = exo.createMessage(exoVideoRenderer)
                .setType(C.MSG_SET_SURFACE)
                .setPayload(surface)
                .send()

        if (!async) {
            message.blockUntilDelivered()
        }
    }

    override fun rewind() {
        logger.info { "Rewinding playback to the start." }
        exo.seekTo(0)
    }

    override fun seekTo(position: Int) {
        logger.info { "Seeking to position $position" }
        exo.seekTo(position.toLong())
    }

    private fun applyVolumeState() {
        exoAudioRenderer?.let { exoAudioRenderer ->
            val volume = if (this.muted) 0f else 1f
            logger.info { "Setting volume on exo player to $volume" }

            exo.createMessage(exoAudioRenderer)
                    .setType(C.MSG_SET_VOLUME)
                    .setPayload(volume)
                    .send()
        }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        logger.info { "onLoadingChanged: $isLoading" }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> callbacks.onVideoBufferingStarts()

            Player.STATE_READY -> {
                // better re-apply volume state
                applyVolumeState()

                if (playWhenReady) {
                    callbacks.onVideoRenderingStarts()
                } else {
                    callbacks.onVideoBufferingEnds()
                }
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
        logger.info { "Timeline has changed" }
    }

    override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        logger.info { "Tracks have changed, ${trackGroups.length} tracks available" }
    }

    override fun onRepeatModeChanged(p0: Int) {
        logger.info { "Repeat mode has changed to $p0" }
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        val rootCause = error.rootCause

        val messageChain = error.getMessageWithCauses()
        when {
            error.type == ExoPlaybackException.TYPE_SOURCE -> {
                val message = context.getString(R.string.media_exo_error_io, rootCause.message)
                callbacks.onVideoError(message, VideoPlayer.ErrorKind.NETWORK)
            }

            messageChain.contains("Top bit not zero:") -> {
                val message = context.getString(R.string.media_exo_error_topbit)
                callbacks.onVideoError(message, VideoPlayer.ErrorKind.NETWORK)
            }

            else -> {
                AndroidUtility.logToCrashlytics(rootCause)
                callbacks.onVideoError(messageChain, VideoPlayer.ErrorKind.UNKNOWN)
            }
        }

        // try to reset the player
        pause()
    }

    override fun onPositionDiscontinuity(reason: Int) {
    }

    override fun onSeekProcessed() {
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
    }

    override fun onPlaybackParametersChanged(params: PlaybackParameters?) {
        logger.info { "Playback parameters are now: $params" }
    }

    private class VideoListener(callbacks: VideoPlayer.Callbacks, parentView: AspectLayout) : VideoRendererEventListener {
        private val callbacks by weakref(callbacks)
        private val parentView by weakref(parentView)

        override fun onVideoEnabled(counters: DecoderCounters) {}
        override fun onVideoDisabled(counters: DecoderCounters) {}

        override fun onVideoDecoderInitialized(decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            logger.info { "Initialized decoder $decoderName after ${initializationDurationMs}ms" }
        }

        override fun onVideoInputFormatChanged(format: Format) {
            logger.info { "Video format is now $format" }
        }

        override fun onDroppedFrames(count: Int, elapsed: Long) {
        }

        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            if (width > 0 && height > 0) {
                val scaledWidth = ((width * pixelWidthHeightRatio) + 0.5f).toInt()

                logger.info { "Got video track with size ${scaledWidth}x$height" }

                this.parentView?.aspect = scaledWidth.toFloat() / height
                this.callbacks?.onVideoSizeChanged(scaledWidth, height)
            }
        }

        override fun onRenderedFirstFrame(surface: Surface?) {
            this.callbacks?.onVideoRenderingStarts()
        }
    }

    private class MediaCodecSelectorImpl internal constructor(private val settings: Settings) : MediaCodecSelector {
        @Throws(MediaCodecUtil.DecoderQueryException::class)
        override fun getDecoderInfos(mimeType: String, requiresSecureDecoder: Boolean): MutableList<MediaCodecInfo> {
            val codecs = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder)
            // logger.info("Codec selector for {} returned: {}", mimeType, Lists.transform(codecs, codec -> codec.name));

            // look fo the best matching codec to return to the user.
            val preference = if (mimeType.startsWith("video/")) settings.videoCodec else settings.audioCodec
            return bestMatchingCodec(codecs, preference)?.let { mutableListOf(it) } ?: codecs
        }

        @Throws(MediaCodecUtil.DecoderQueryException::class)
        override fun getPassthroughDecoderInfo(): MediaCodecInfo? {
            return MediaCodecSelector.DEFAULT.passthroughDecoderInfo
        }
    }

    private class DataSourceFactory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val cache by context.kodein.instance<Cache>()
            return InputStreamCacheDataSource(cache)
        }
    }

    companion object {
        private val logger = Logger("ExoVideoPlayer")

        private fun bestMatchingCodec(codecs: List<MediaCodecInfo>, videoCodecName: String): MediaCodecInfo? {
            return when (videoCodecName) {
                "software" -> codecs.firstOrNull { isSoftwareDecoder(it) }
                "hardware" -> codecs.firstOrNull { isHardwareDecoder(it) }
                else -> codecs.firstOrNull { it.name.equals(videoCodecName, ignoreCase = true) }
            }
        }

        private fun isSoftwareDecoder(codec: MediaCodecInfo): Boolean {
            val name = codec.name.toLowerCase()
            return name.startsWith("omx.google.") || name.startsWith("omx.ffmpeg.") || name.contains(".sw.")
        }

        private fun isHardwareDecoder(codec: MediaCodecInfo): Boolean {
            return !isSoftwareDecoder(codec)
        }
    }
}