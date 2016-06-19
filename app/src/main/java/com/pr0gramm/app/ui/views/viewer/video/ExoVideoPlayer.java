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
import android.view.Surface;
import android.view.View;

import com.google.android.exoplayer.DecoderInfo;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.FileDataSource;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.views.AspectLayout;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import okhttp3.OkHttpClient;

import static com.pr0gramm.app.util.AndroidUtility.getMessageWithCauses;

/**
 * Stripped down version of {@link android.widget.VideoView}.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ExoVideoPlayer extends RxVideoPlayer implements VideoPlayer, ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener {
    private static final Logger logger = LoggerFactory.getLogger("ExoVideoPlayer");

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 64;
    private final Context context;
    private final AspectLayout parentView;
    private final Settings settings;

    private boolean muted;

    private ExoPlayer exo;
    private MediaCodecVideoTrackRenderer exoVideoTrack;
    private MediaCodecAudioTrackRenderer exoAudioTrack;
    private BufferedDataSource dataSource;
    private boolean initialized;

    private Uri uri;
    private ViewBackend surfaceProvider;

    public ExoVideoPlayer(Context context, AspectLayout aspectLayout) {
        this.context = context;
        this.parentView = aspectLayout;
        this.settings = Settings.of(context);

        // Use a texture view to display the video.
        surfaceProvider = new TextureViewBackend(context, backendViewCallbacks);
        View videoView = surfaceProvider.getView();
        parentView.addView(videoView);

        logger.info("Create ExoPlayer instance");
        exo = ExoPlayer.Factory.newInstance(2);
        exo.addListener(this);

        RxView.detaches(videoView).subscribe(event -> {
            logger.info("Detaching view, releasing exo player now.");
            exo.release();
        });
    }

    @Override
    public void open(Uri uri) {
        logger.info("Opening exo player for uri {}", uri);

        if ("file".equals(uri.getScheme())) {
            logger.info("Got a local file, reading directly from that file.");
            dataSource = new ForwardingDataSource(new FileDataSource()) {
                @Override
                public float buffered() {
                    // always fully buffered.
                    return 1;
                }
            };
        } else {
            logger.info("Got a remote file, using caching source.");
            OkHttpClient httpClient = Dagger.appComponent(context).okHttpClient();
            dataSource = new InputStreamCacheDataSource(context, httpClient, uri);
        }

        this.uri = uri;
    }

    @Override
    public void start() {
        if (initialized)
            return;

        logger.info("Preparing exo player now'");

        ExtractorSampleSource sampleSource = new ExtractorSampleSource(
                uri, dataSource,
                new DefaultAllocator(BUFFER_SEGMENT_SIZE),
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE,
                new Mp4Extractor(), new WebmExtractor());

        exoVideoTrack = new MediaCodecVideoTrackRenderer(
                context, sampleSource, mediaCodecSelector,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
                5000, new Handler(Looper.getMainLooper()), this, 50);

        exoAudioTrack = new MediaCodecAudioTrackRenderer(sampleSource, mediaCodecSelector);

        exo.prepare(exoVideoTrack, exoAudioTrack);
        exo.setPlayWhenReady(true);

        // initialize the renderer with a surface, if we already have one.
        // this might be the case, if we are restarting the video after
        // a call to pause.
        if (surfaceProvider.hasSurface()) {
            exo.sendMessage(exoVideoTrack,
                    MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                    surfaceProvider.getCurrentSurface());
        }

        initialized = true;
    }

    @Override
    public float progress() {
        float duration = exo.getDuration();
        return duration > 0 ? exo.getCurrentPosition() / duration : -1;
    }

    @Override
    public float buffered() {
        return dataSource.buffered();
    }

    @Override
    public void pause() {
        if (!initialized)
            return;

        logger.info("Stopping exo player now");
        exo.blockingSendMessage(exoVideoTrack,
                MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                null);

        exo.stop();

        initialized = false;

        // we dont need those anymore.
        exoAudioTrack = null;
        exoVideoTrack = null;
    }

    @Override
    public void rewind() {
        logger.info("Rewinding playback to the start.");
        exo.seekTo(0);
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public void setMuted(boolean muted) {
        if (exoAudioTrack != null) {
            float volume = muted ? 0.f : 1.f;
            exo.sendMessage(exoAudioTrack, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, volume);
        }

        this.muted = muted;
    }


    @SuppressWarnings("FieldCanBeLocal")
    private final ViewBackend.Callbacks backendViewCallbacks = new ViewBackend.Callbacks() {
        @Override
        public void onAvailable(ViewBackend backend) {
            if (exoVideoTrack != null) {
                exo.sendMessage(exoVideoTrack,
                        MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                        backend.getCurrentSurface());
            }
        }

        @Override
        public void onSizeChanged(ViewBackend backend, int width, int height) {
        }

        @Override
        public void onDestroy(ViewBackend backend) {
            if (exoVideoTrack != null) {
                exo.blockingSendMessage(exoVideoTrack,
                        MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                        null);
            }
        }
    };

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
            case ExoPlayer.STATE_PREPARING:
                callbacks.onVideoBufferingStarts();
                break;

            case ExoPlayer.STATE_READY:
                // better re-apply mute state
                setMuted(muted);

                if (playWhenReady) {
                    callbacks.onVideoRenderingStarts();
                } else {
                    callbacks.onVideoBufferingEnds();
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
        Throwable rootCause = Throwables.getRootCause(error);

        String messageChain = getMessageWithCauses(error);
        if (messageChain.contains("::pr0:: network error")) {
            String message = context.getString(R.string.media_exo_error_io, rootCause.getMessage());
            callbacks.onVideoError(message, ErrorKind.NETWORK);
            return;

        } else {
            callbacks.onVideoError(messageChain, ErrorKind.UNKNOWN);

            AndroidUtility.logToCrashlytics(rootCause);
        }

        // try to reset the player
        pause();
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        logger.warn("Dropped {} frames", count);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (width > 0 && height > 0) {
            int scaledWidth = (int) ((width * pixelWidthHeightRatio) + 0.5f);

            logger.info("Got video track with size {}x{}", scaledWidth, height);
            parentView.setAspect((float) scaledWidth / height);
            callbacks.onVideoSizeChanged(scaledWidth, height);
        }
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        callbacks.onVideoRenderingStarts();
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        callbacks.onVideoError(getMessageWithCauses(e), ErrorKind.UNKNOWN);
        AndroidUtility.logToCrashlytics(e);
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        callbacks.onVideoError(getMessageWithCauses(e), ErrorKind.UNKNOWN);
        AndroidUtility.logToCrashlytics(e);
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
        logger.info("Initialized decoder {} after {}ms", decoderName, initializationDurationMs);
    }

    private final MediaCodecSelector mediaCodecSelector = new MediaCodecSelector() {
        @Override
        public DecoderInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
            List<DecoderInfo> codecs = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);
            logger.info("Available codecs for {}: {}", mimeType, Lists.transform(codecs, codec -> codec.name));

            // default is the first one.
            DecoderInfo systemDefaultDecoder = codecs.size() > 0 ? codecs.get(0) : null;

            if (settings.useSoftwareDecoder()) {
                // try the google codec first, fall back on the "default" if no google codec found.
                return FluentIterable.from(codecs)
                        .firstMatch(codec -> codec.name.startsWith("OMX.google."))
                        .or(Optional.fromNullable(systemDefaultDecoder))
                        .orNull();
            } else {
                return systemDefaultDecoder;
            }
        }

        @Override
        public DecoderInfo getPassthroughDecoderInfo() throws MediaCodecUtil.DecoderQueryException {
            return MediaCodecSelector.DEFAULT.getPassthroughDecoderInfo();
        }
    };
}