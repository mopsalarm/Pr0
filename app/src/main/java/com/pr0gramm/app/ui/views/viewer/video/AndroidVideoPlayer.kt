/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pr0gramm.app.ui.views.viewer.video

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.StringRes
import com.jakewharton.rxbinding.view.RxView
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.util.logger
import java.io.IOException

/**
 * Stripped down version of [android.widget.VideoView].
 */
class AndroidVideoPlayer(private val context: Context, internal val parentView: AspectLayout) :
        RxVideoPlayer(), VideoPlayer {

    // settable by the client
    private var mUri: Uri? = null

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    internal var mCurrentState = STATE_IDLE
    internal var mTargetState = STATE_IDLE

    internal var mMediaPlayer: MediaPlayer? = null
    internal var mVideoWidth: Int = 0
    internal var mVideoHeight: Int = 0
    internal var mSurfaceWidth: Int = 0
    internal var mSurfaceHeight: Int = 0
    internal var mSeekWhenPrepared: Int = 0
    private var mVolume: Int = 0

    // the backend view.
    internal var mBackendView: ViewBackend

    private var shouldShowIoError = true

    override var buffered: Float = 0f

    private var mLastPosition: Int = 0

    private val backendViewCallbacks = object : ViewBackend.Callbacks {
        override fun onAvailable(backend: ViewBackend) {
            openVideo()
        }

        override fun onSizeChanged(backend: ViewBackend, width: Int, height: Int) {
            mSurfaceWidth = width
            mSurfaceHeight = height
            val isValidState = mTargetState == STATE_PLAYING

            val hasValidSize = width > 0 && height > 0
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared)
                }
                start()
            }
        }

        override fun onDestroy(backend: ViewBackend) {
            release()
        }
    }

    init {
        // always use surface view.
        mBackendView = if (Settings.get().useTextureView) {
            TextureViewBackend(context, backendViewCallbacks)
        } else {
            SurfaceViewBackend(context, backendViewCallbacks)
        }

        val view = mBackendView.view
        view.alpha = 0.01f
        parentView.addView(view)

        // forward events
        RxView.detaches(view).map { Unit }.subscribe(detaches)
    }

    override val progress: Float get() {
        val duration = duration.toFloat()
        return if (duration > 0) currentPosition / duration else -1f
    }

    override val duration: Int get() {
        if (isInPlaybackState) {
            return mMediaPlayer!!.duration
        }

        return -1
    }

    override val currentPosition: Int get() {
        if (isInPlaybackState) {
            return mMediaPlayer!!.currentPosition
        }

        return mLastPosition
    }

    internal fun openVideo() {
        val uri = mUri
        if (uri == null || !mBackendView.hasSurface) {
            // not ready for playback just yet, will try again later
            return
        }

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release()

        // lets simulate buffering while preparing...
        callbacks.onVideoBufferingStarts()

        try {
            mMediaPlayer = MediaPlayer().apply {
                setSurface(mBackendView.currentSurface)

                setVolume(mVolume.toFloat(), mVolume.toFloat())
                setOnPreparedListener(mPreparedListener)
                setOnVideoSizeChangedListener(mSizeChangedListener)
                setOnCompletionListener(mCompletionListener)
                setOnErrorListener(mErrorListener)
                setOnBufferingUpdateListener { _, percent -> buffered = 0.01f * percent }
                setOnInfoListener(mInfoListener)
                setDataSource(context, uri)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                prepareAsync()
            }


            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING

        } catch (ex: IOException) {
            logger.warn("Unable to open content: " + uri, ex)
            mCurrentState = STATE_ERROR
            mTargetState = STATE_ERROR
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)

        } catch (ex: IllegalArgumentException) {
            logger.warn("Unable to open content: " + uri, ex)
            mCurrentState = STATE_ERROR
            mTargetState = STATE_ERROR
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)

        } catch (ex: IllegalStateException) {
            logger.warn("Unable to open content: " + uri, ex)
            mCurrentState = STATE_ERROR
            mTargetState = STATE_ERROR
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        }

    }

    override var muted: Boolean
        get() = mVolume < 0.5
        set(muted) {
            mVolume = if (muted) 0 else 1
            mMediaPlayer?.setVolume(mVolume.toFloat(), mVolume.toFloat())
        }

    internal val mSizeChangedListener: MediaPlayer.OnVideoSizeChangedListener = MediaPlayer.OnVideoSizeChangedListener { mp, width, height ->
        mVideoWidth = mp.videoWidth
        mVideoHeight = mp.videoHeight
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            mBackendView.size = ViewBackend.Size(mVideoWidth, mVideoHeight)

            logger.info("set video aspect to {}x{}", mVideoWidth, mVideoHeight)
            parentView.aspect = mVideoWidth.toFloat() / mVideoHeight
        }

        callbacks.onVideoSizeChanged(width, height)
    }

    internal val mPreparedListener: MediaPlayer.OnPreparedListener = MediaPlayer.OnPreparedListener { mp ->
        mCurrentState = STATE_PREPARED

        mp.isLooping = true

        mVideoWidth = mp.videoWidth
        mVideoHeight = mp.videoHeight

        val seekToPosition = mSeekWhenPrepared  // mSeekWhenPrepared may be changed after seekTo() call
        if (seekToPosition != 0) {
            seekTo(seekToPosition)
        }
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            logger.info("set video aspect to {}x{}", mVideoWidth, mVideoHeight)
            mBackendView.size = ViewBackend.Size(mVideoWidth, mVideoHeight)
            parentView.aspect = mVideoWidth.toFloat() / mVideoHeight

            if (mSurfaceWidth != 0 && mSurfaceHeight != 0) {
                // We didn't actually change the size (it was already at the size
                // we need), so we won't get a "surface changed" callback, so
                // start the video here instead of in the callback.
                if (mTargetState == STATE_PLAYING) {
                    start()
                }
            }
        } else {
            // We don't know the video size yet, but should start anyway.
            // The video size might be reported to us later.
            if (mTargetState == STATE_PLAYING) {
                start()
            }
        }
    }

    private val mCompletionListener = MediaPlayer.OnCompletionListener { mp ->
        // Workaround for samsung devices to enable looping.
        if (mp.isLooping) {
            mp.pause()
            mp.seekTo(0)
            mp.start()
        } else {
            mCurrentState = STATE_PLAYBACK_COMPLETED
            mTargetState = STATE_PLAYBACK_COMPLETED
        }
    }

    private val mInfoListener = MediaPlayer.OnInfoListener { _, event, _ ->
        logger.info("Info event: {}", event)

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            callbacks.onVideoBufferingStarts()
        }

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            callbacks.onVideoBufferingEnds()
        }

        if (event == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            callbacks.onVideoRenderingStarts()
        }

        true
    }

    private val mErrorListener = MediaPlayer.OnErrorListener { _, frameworkErrorCode, implErrorCode ->
        logger.error("Error: $frameworkErrorCode,$implErrorCode")
        mCurrentState = STATE_ERROR
        mTargetState = STATE_ERROR

        try {
            handleError(frameworkErrorCode, implErrorCode)
        } catch (ignored: Exception) {
        }

        true
    }

    private fun handleError(what: Int, extra: Int) {
        logger.error("media player error occurred: {} {}", what, extra)

        if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == MediaPlayer.MEDIA_ERROR_IO) {
            if (shouldShowIoError) {
                callbacks.onVideoError(context.getString(R.string.could_not_play_video_io), VideoPlayer.ErrorKind.NETWORK)
                shouldShowIoError = false
            }

            callbacks.onVideoBufferingEnds()
            return
        }

        var kind: VideoPlayer.ErrorKind = VideoPlayer.ErrorKind.UNKNOWN
        @StringRes
        val errorMessage: Int = when (what) {
            MediaPlayer.MEDIA_ERROR_IO -> {
                kind = VideoPlayer.ErrorKind.NETWORK
                R.string.media_error_io
            }

            MediaPlayer.MEDIA_ERROR_MALFORMED -> R.string.media_error_malformed
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> R.string.media_error_server_died
            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> R.string.media_error_timed_out
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> R.string.media_error_unknown
            MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> R.string.media_error_unsupported

            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK ->
                R.string.media_error_not_valid_for_progressive_playback

            else -> R.string.could_not_play_video
        }

        // show this error.
        callbacks.onVideoError(context.getString(errorMessage), kind)
    }


    /*
     * release the media player in any state
     */
    internal fun release() {
        mBackendView.view.alpha = 0.01f

        if (mMediaPlayer != null) {
            mLastPosition = currentPosition

            mMediaPlayer!!.reset()
            mMediaPlayer!!.release()
            mMediaPlayer = null
            mCurrentState = STATE_IDLE
        }
    }

    override fun open(uri: Uri) {
        mLastPosition = mSeekWhenPrepared
        mUri = uri
        openVideo()
    }

    override fun start() {
        if (isInPlaybackState) {
            mBackendView.view.alpha = 1f

            mMediaPlayer!!.start()
            mCurrentState = STATE_PLAYING

            callbacks.onVideoBufferingEnds()

        } else {
            if (mCurrentState != STATE_PREPARING) {
                openVideo()
            }
        }

        mTargetState = STATE_PLAYING
    }

    override fun pause() {
        release()
        mTargetState = STATE_IDLE
    }

    override fun rewind() {
        seekTo(0)
    }

    override fun seekTo(position: Int) {
        if (isInPlaybackState) {
            mMediaPlayer!!.seekTo(position)
            mSeekWhenPrepared = 0
        } else {
            mSeekWhenPrepared = position
        }
    }

    private val isInPlaybackState: Boolean get() = mMediaPlayer != null &&
            mCurrentState != STATE_ERROR &&
            mCurrentState != STATE_IDLE &&
            mCurrentState != STATE_PREPARING

    companion object {
        internal val logger = logger("CustomVideoView")

        // all possible internal states
        private val STATE_ERROR = -1
        private val STATE_IDLE = 0
        private val STATE_PREPARING = 1
        private val STATE_PREPARED = 2
        private val STATE_PLAYING = 3
        private val STATE_PAUSED = 4
        private val STATE_PLAYBACK_COMPLETED = 5
    }
}