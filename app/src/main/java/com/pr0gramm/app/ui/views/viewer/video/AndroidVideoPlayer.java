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
import android.util.AttributeSet;
import android.view.View;

import com.pr0gramm.app.R;
import com.pr0gramm.app.Stats;
import com.pr0gramm.app.ui.views.AspectLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Stripped down version of {@link android.widget.VideoView}.
 */
public class AndroidVideoPlayer extends AspectLayout implements VideoPlayer {
    private static final Logger logger = LoggerFactory.getLogger("CustomVideoView");

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

    int retryCount;
    private boolean shouldShowIoError = true;

    private Callbacks videoCallbacks = new NoopVideoCallbacks();

    public AndroidVideoPlayer(Context context) {
        this(context, null);
    }

    public AndroidVideoPlayer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AndroidVideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVideoView();
    }

    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;

        // always use surface view.
        mBackendView = new TextureViewBackend(getContext(), backendViewCallbacks);

        View view = mBackendView.getView();
        view.setAlpha(0.01f);
        addView(view);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mBackendView.getView().layout(0, 0, right - left, bottom - top);
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    public void setVideoURI(Uri uri) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    private void openVideo() {
        if (mUri == null || !mBackendView.hasSurface()) {
            // not ready for playback just yet, will try again later
            return;
        }

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release();
        try {
            mMediaPlayer = new MediaPlayer();

            mBackendView.setSurface(mMediaPlayer);
            mMediaPlayer.setVolume(mVolume, mVolume);
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setDataSource(getContext(), mUri);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
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
                        setAspect((float) mVideoWidth / mVideoHeight);
                    }

                    videoCallbacks.onVideoSizeChanged(width, height);
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
                setAspect((float) mVideoWidth / mVideoHeight);

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

    private final MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    // Workaround for samsung devices to enable looping.
                    if (mp.isLooping()) {
                        mp.pause();
                        mp.seekTo(0);
                        mp.start();
                        return;
                    }

                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                }
            };

    private final MediaPlayer.OnInfoListener mInfoListener = (mp, event, extra) -> {
        logger.info("Info event: {}", event);

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            videoCallbacks.onVideoBufferingStarts();
        }

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            videoCallbacks.onVideoBufferingEnds();
        }

        if (event == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            videoCallbacks.onVideoBufferingEnds();
            videoCallbacks.onVideoRenderingStarts();
            // Stats.get().increment("video.playback.succeeded");
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
                videoCallbacks.onVideoError(getContext().getString(R.string.could_not_play_video_io));
                shouldShowIoError = false;
            }

            videoCallbacks.onVideoBufferingEnds();
            return;
        }

        String errorKey;
        @StringRes int errorMessage;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_IO:
                errorMessage = R.string.media_error_io;
                errorKey = "MEDIA_ERROR_IO";
                break;

            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                errorMessage = R.string.media_error_malformed;
                errorKey = "MEDIA_ERROR_MALFORMED";
                break;

            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                errorMessage = R.string.media_error_server_died;
                errorKey = "MEDIA_ERROR_SERVER_DIED";
                break;

            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                errorMessage = R.string.media_error_timed_out;
                errorKey = "MEDIA_ERROR_TIMED_OUT";
                break;

            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                errorMessage = R.string.media_error_unknown;
                errorKey = "MEDIA_ERROR_UNKNOWN";
                break;

            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                errorMessage = R.string.media_error_unsupported;
                errorKey = "MEDIA_ERROR_UNSUPPORTED";
                break;

            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                errorMessage = R.string.media_error_not_valid_for_progressive_playback;
                errorKey = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
                break;

            default:
                errorMessage = R.string.could_not_play_video;
                errorKey = "UNKNOWN-" + what;
                break;
        }

        // show this error.
        videoCallbacks.onVideoError(getContext().getString(errorMessage));

        Stats.get().incrementCounter("video.playback.failed", "reason:" + errorKey);
    }

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
    public void setVideoCallbacks(Callbacks callbacks) {
        this.videoCallbacks = callbacks != null ? callbacks : new NoopVideoCallbacks();
    }

    @Override
    public void open(Uri uri) {
        setVideoURI(uri);
    }

    public void start() {
        if (isInPlaybackState()) {
            mBackendView.getView().setAlpha(1f);

            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public float progress() {
        float duration = getDuration();
        return duration > 0 ? getCurrentPosition() / duration : -1;
    }

    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
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

    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    public boolean isTransformable() {
        return true;
    }
}