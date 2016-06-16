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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.ui.views.AspectLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;

/**
 * Stripped down version of {@link android.widget.VideoView}.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ExoVideoPlayer extends AspectLayout implements VideoPlayer, ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener {
    private static final Logger logger = LoggerFactory.getLogger("ExoVideoPlayer");

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 64;

    private ExoPlayer exo;
    private MediaCodecVideoTrackRenderer exoVideoTrack;
    private MediaCodecAudioTrackRenderer exoAudioTrack;

    private Callbacks videoCallbacks;
    private ViewBackend backendView;

    public ExoVideoPlayer(Context context) {
        super(context);
        init();
    }

    public ExoVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExoVideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // always use a texture view.
        backendView = new TextureViewBackend(getContext(), backendViewCallbacks);

        View view = backendView.getView();
        // view.setAlpha(0.01f);
        addView(view);

        logger.info("Create ExoPlayer instance");
        exo = ExoPlayer.Factory.newInstance(2);
        exo.addListener(this);

        RxView.detaches(this).subscribe(event -> {
            logger.info("Detaching view, releasing exo player now.");
            exo.release();
        });
    }

    @Override
    public void open(Uri uri) {
        logger.info("Opening exo player for uri {}", uri);

        OkHttpClient httpClient = Dagger.appComponent(getContext()).okHttpClient();

        DataSource dataSource = new InputStreamCacheDataSource(getContext(), httpClient, uri);

        ExtractorSampleSource sampleSource = new ExtractorSampleSource(
                uri, dataSource,
                new DefaultAllocator(BUFFER_SEGMENT_SIZE),
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE,
                new Mp4Extractor(), new WebmExtractor());

        exoVideoTrack = new MediaCodecVideoTrackRenderer(
                getContext(), sampleSource, MediaCodecSelector.DEFAULT,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
                5000, new Handler(Looper.getMainLooper()), this, 50);


        exoAudioTrack = new MediaCodecAudioTrackRenderer(
                sampleSource, MediaCodecSelector.DEFAULT);

        exo.prepare(exoVideoTrack, exoAudioTrack);
    }

    @Override
    public void setVideoCallbacks(@Nullable Callbacks callbacks) {
        this.videoCallbacks = callbacks;
    }

    @Override
    public void start() {
        logger.info("Set playback to 'play'");
        exo.setPlayWhenReady(true);
    }

    @Override
    public float progress() {
        float duration = exo.getDuration();
        return duration > 0 ? exo.getCurrentPosition() / duration : -1;
    }

    @Override
    public void pause() {
        logger.info("Set playback to 'pause'");
        exo.setPlayWhenReady(false);
    }

    @Override
    public void rewind() {
        logger.info("Rewinding playback");
        exo.seekTo(0);
    }

    @Override
    public boolean isMuted() {
        return false;
    }

    @Override
    public void setMuted(boolean muted) {

    }


    private ViewBackend.Callbacks backendViewCallbacks = new ViewBackend.Callbacks() {
        @Override
        public void onAvailable(ViewBackend backend) {
            exo.sendMessage(exoVideoTrack,
                    MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                    backend.getCurrentSurface());
        }

        @Override
        public void onSizeChanged(ViewBackend backend, int width, int height) {
        }

        @Override
        public void onDestroy(ViewBackend backend) {
            exo.blockingSendMessage(exoVideoTrack,
                    MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                    backend.getCurrentSurface());
        }
    };

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
            case ExoPlayer.STATE_PREPARING:
                videoCallbacks.onVideoBufferingStarts();
                break;

            case ExoPlayer.STATE_READY:
                videoCallbacks.onVideoBufferingEnds();

                if (playWhenReady) {
                    videoCallbacks.onVideoRenderingStarts();
                }

                break;

            case ExoPlayer.STATE_ENDED:
                rewind();
                break;
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        videoCallbacks.onVideoError(error.getLocalizedMessage());
        if (exo.getPlaybackState() != ExoPlayer.STATE_IDLE)
            exo.release();
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {

    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (width > 0 && height > 0) {
            int scaledWidth = (int) ((width * pixelWidthHeightRatio) + 0.5f);

            setAspect((float) scaledWidth / height);
            videoCallbacks.onVideoSizeChanged(scaledWidth, height);
        }
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        videoCallbacks.onVideoRenderingStarts();
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        videoCallbacks.onVideoError(e.getMessage());
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        videoCallbacks.onVideoError(e.getMessage());
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
        logger.info("Initialized decoder {} after {}ms", decoderName, initializationDurationMs);
    }
}