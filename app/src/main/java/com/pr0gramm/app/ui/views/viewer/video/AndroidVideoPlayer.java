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

package com.pr0gramm.app.ui.views.viewer.video;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.StringRes;
import android.view.View;

import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.views.AspectLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Stripped down version of {@link android.widget.VideoView}.
 */
public class AndroidVideoPlayer extends RxVideoPlayer implements VideoPlayer {
    private static final Logger logger = LoggerFactory.getLogger("CustomVideoView");
    private final Context context;
    private final AspectLayout parentView;

    // settable by the client
    private Uri mUri;

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private MediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mSeekWhenPrepared;
    private int mVolume;

    // the backend view.
    private ViewBackend mBackendView;

    private boolean shouldShowIoError = true;

    private float buffered;

    public AndroidVideoPlayer(Context context, AspectLayout aspectLayout) {
        this.context = context;
        this.parentView = aspectLayout;

        // always use surface view.
        mBackendView = new TextureViewBackend(context, backendViewCallbacks);

        View view = mBackendView.getView();
        view.setAlpha(0.01f);
        parentView.addView(view);
    }

    private void openVideo() {
        if (mUri == null || !mBackendView.hasSurface()) {
            // not ready for playback just yet, will try again later
            return;
        }

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release();

        // lets simulate buffering while preparing...
        callbacks.onVideoBufferingStarts();

        try {
            mMediaPlayer = new MediaPlayer();

            mMediaPlayer.setSurface(mBackendView.getCurrentSurface());

            mMediaPlayer.setVolume(mVolume, mVolume);
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setDataSource(context, mUri);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnBufferingUpdateListener((mediaPlayer, percent) -> {
                this.buffered = 0.01f * percent;
            });

            mMediaPlayer.prepareAsync();

            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            logger.warn("Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    public void setMuted(boolean muted) {
        mVolume = muted ? 0 : 1;
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(mVolume, mVolume);
        }
    }

    public boolean isMuted() {
        return mVolume < 0.5;
    }

    final MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        mBackendView.setSize(mVideoWidth, mVideoHeight);

                        logger.info("set video aspect to {}x{}", mVideoWidth, mVideoHeight);
                        parentView.setAspect((float) mVideoWidth / mVideoHeight);
                    }

                    callbacks.onVideoSizeChanged(width, height);
                }
            };

    final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            mp.setLooping(true);

            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                logger.info("set video aspect to {}x{}", mVideoWidth, mVideoHeight);
                mBackendView.setSize(mVideoWidth, mVideoHeight);
                parentView.setAspect((float) mVideoWidth / mVideoHeight);

                if (mSurfaceWidth != 0 && mSurfaceHeight != 0) {
                    // We didn't actually change the size (it was already at the size
                    // we need), so we won't get a "surface changed" callback, so
                    // start the video here instead of in the callback.
                    if (mTargetState == STATE_PLAYING) {
                        start();
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private final MediaPlayer.OnCompletionListener mCompletionListener = mp -> {
        // Workaround for samsung devices to enable looping.
        if (mp.isLooping()) {
            mp.pause();
            mp.seekTo(0);
            mp.start();
            return;
        }

        mCurrentState = STATE_PLAYBACK_COMPLETED;
        mTargetState = STATE_PLAYBACK_COMPLETED;
    };

    private final MediaPlayer.OnInfoListener mInfoListener = (mp, event, extra) -> {
        logger.info("Info event: {}", event);

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            callbacks.onVideoBufferingStarts();
        }

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            callbacks.onVideoBufferingEnds();
        }

        if (event == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            callbacks.onVideoRenderingStarts();
        }

        return true;
    };

    private final MediaPlayer.OnErrorListener mErrorListener = (mp, frameworkErrorCode, implErrorCode) -> {
        logger.error("Error: " + frameworkErrorCode + "," + implErrorCode);
        mCurrentState = STATE_ERROR;
        mTargetState = STATE_ERROR;

        try {
            handleError(frameworkErrorCode, implErrorCode);
        } catch (Exception ignored) {
        }

        return true;
    };

    private void handleError(int what, int extra) {
        logger.error("media player error occurred: {} {}", what, extra);

        if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == MediaPlayer.MEDIA_ERROR_IO) {
            if (shouldShowIoError) {
                callbacks.onVideoError(context.getString(R.string.could_not_play_video_io), ErrorKind.NETWORK);
                shouldShowIoError = false;
            }

            callbacks.onVideoBufferingEnds();
            return;
        }

        ErrorKind kind = ErrorKind.UNKNOWN;
        @StringRes int errorMessage;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_IO:
                errorMessage = R.string.media_error_io;
                kind = ErrorKind.NETWORK;
                break;

            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                errorMessage = R.string.media_error_malformed;
                break;

            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                errorMessage = R.string.media_error_server_died;
                break;

            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                errorMessage = R.string.media_error_timed_out;
                break;

            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                errorMessage = R.string.media_error_unknown;
                break;

            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                errorMessage = R.string.media_error_unsupported;
                break;

            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                errorMessage = R.string.media_error_not_valid_for_progressive_playback;
                break;

            default:
                errorMessage = R.string.could_not_play_video;
                break;
        }

        // show this error.
        callbacks.onVideoError(context.getString(errorMessage), kind);
    }

    @SuppressWarnings("FieldCanBeLocal")
    final private ViewBackend.Callbacks backendViewCallbacks = new ViewBackend.Callbacks() {
        @Override
        public void onAvailable(ViewBackend backend) {
            openVideo();
        }

        @Override
        public void onSizeChanged(ViewBackend backend, int width, int height) {
            mSurfaceWidth = width;
            mSurfaceHeight = height;
            boolean isValidState = (mTargetState == STATE_PLAYING);

            boolean hasValidSize = (width > 0 && height > 0);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        @Override
        public void onDestroy(ViewBackend backend) {
            release();
        }
    };

    /*
     * release the media player in any state
     */
    private void release() {
        mBackendView.getView().setAlpha(0.01f);

        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
        }
    }

    @Override
    public void open(Uri uri) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
    }

    public void start() {
        if (isInPlaybackState()) {
            mBackendView.getView().setAlpha(1f);

            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;

            callbacks.onVideoBufferingEnds();

        } else {
            if (mCurrentState != STATE_PREPARING) {
                openVideo();
            }
        }

        mTargetState = STATE_PLAYING;
    }

    @Override
    public float progress() {
        float duration = getDuration();
        return duration > 0 ? getCurrentPosition() / duration : -1;
    }

    @Override
    public float buffered() {
        return buffered;
    }

    @Override
    public void pause() {
        release();
        mTargetState = STATE_IDLE;
    }

    @Override
    public void rewind() {
        seekTo(0);
    }

    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }

        return -1;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public int currentPosition() {
        return getCurrentPosition();
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }
}