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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.io.Cache;
import com.pr0gramm.app.ui.views.AspectLayout;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import static com.google.common.collect.FluentIterable.from;
import static com.pr0gramm.app.util.AndroidUtility.getMessageWithCauses;

/**
 * Stripped down version of {@link android.widget.VideoView}.
 */
@SuppressWarnings("WeakerAccess")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ExoVideoPlayer extends RxVideoPlayer implements VideoPlayer, ExoPlayer.EventListener {

    static final Logger logger = LoggerFactory.getLogger("ExoVideoPlayer");

    private static final int MAX_DROPPED_FRAMES = 75;

    private final Context context;
    private final AspectLayout parentView;
    private final Handler handler;

    final ExoPlayer exo;
    final Settings settings;
    final MediaCodecVideoRenderer exoVideoRenderer;

    @Nullable
    private MediaCodecAudioRenderer exoAudioRenderer;

    private boolean muted;

    private Uri uri;
    private ViewBackend surfaceProvider;
    private boolean initialized;

    public ExoVideoPlayer(Context context, boolean hasAudio, AspectLayout aspectLayout) {
        this.context = context.getApplicationContext();
        this.parentView = aspectLayout;
        this.settings = Settings.of(context);

        this.handler = new Handler(Looper.getMainLooper());

        // Use a texture view to display the video.
        surfaceProvider = settings.useTextureView()
                ? new TextureViewBackend(context, backendViewCallbacks)
                : new SurfaceViewBackend(context, backendViewCallbacks);

        View videoView = surfaceProvider.getView();
        parentView.addView(videoView);

        logger.info("Create ExoPlayer instance");

        VideoListener videoListener = new VideoListener(callbacks, parentView);

        MediaCodecSelector mediaCodecSelector = new MediaCodecSelectorImpl(settings);
        exoVideoRenderer = new MediaCodecVideoRenderer(context, mediaCodecSelector,
                5000, handler, videoListener, MAX_DROPPED_FRAMES);

        Renderer[] renderers;
        if (hasAudio) {
            exoAudioRenderer = new MediaCodecAudioRenderer(mediaCodecSelector);
            renderers = new Renderer[]{exoVideoRenderer, exoAudioRenderer};
        } else {
            renderers = new Renderer[]{exoVideoRenderer};
        }

        exo = ExoPlayerFactory.newInstance(renderers, new DefaultTrackSelector(new FixedTrackSelection.Factory()));
        exo.addListener(this);

        RxView.detaches(videoView).subscribe(event -> {
            detaches.onNext(null);

            pause();

            logger.info("Detaching view, releasing exo player now.");
            exo.removeListener(this);
            exo.release();
        });
    }

    @Override
    public void open(Uri uri) {
        logger.info("Opening exo player for uri {}", uri);
        this.uri = uri;
    }

    @Override
    public void start() {
        if (initialized)
            return;

        initialized = true;

        ExtractorsFactory extractorsFactory = () -> new Extractor[]{
                new FragmentedMp4Extractor(), new Mp4Extractor()};

        MediaSource mediaSource = new LoopingMediaSource(new ExtractorMediaSource(uri,
                new DataSourceFactory(context, uri), extractorsFactory, handler,
                new MediaSourceListener(callbacks)));

        // apply volume before starting the player
        applyVolumeState();

        logger.info("Preparing exo player now'");

        exo.prepare(mediaSource, false, false);
        exo.setPlayWhenReady(true);

        applyVolumeState();

        // initialize the renderer with a surface, if we already have one.
        // this might be the case, if we are restarting the video after
        // a call to pause.
        if (surfaceProvider.hasSurface()) {
            sendSetSurfaceMessage(true, surfaceProvider.getCurrentSurface());
        }
    }

    @Override
    public float progress() {
        float duration = exo.getDuration();
        return duration > 0 ? exo.getCurrentPosition() / duration : -1;
    }

    @Override
    public float buffered() {
        float buffered = exo.getBufferedPercentage() / 100.f;
        if (buffered == 0) {
            buffered = -1;
        }

        return buffered;
    }

    @Override
    public void pause() {
        logger.info("Stopping exo player now");
        sendSetSurfaceMessage(false, null);
        exo.stop();
        initialized = false;
    }

    void sendSetSurfaceMessage(boolean async, @Nullable Surface surface) {
        ExoPlayer.ExoPlayerMessage message = new ExoPlayer.ExoPlayerMessage(
                exoVideoRenderer, C.MSG_SET_SURFACE, surface);

        if (async) {
            exo.sendMessages(message);
        } else {
            exo.blockingSendMessages(message);
        }
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
        this.muted = muted;
        applyVolumeState();
    }

    @Override
    public void seekTo(int position) {
        logger.info("Seeking to position {}", position);
        exo.seekTo(position);
    }

    @Override
    public int currentPosition() {
        return (int) exo.getCurrentPosition();
    }

    @Override
    public int duration() {
        return (int) exo.getDuration();
    }

    private void applyVolumeState() {
        if (exoAudioRenderer != null) {
            float volume = muted ? 0.f : 1.f;
            logger.info("Setting volume on exo player to {}", volume);

            // exo.sendMessages(1, muted ? -1 : 0);
            exo.sendMessages(new ExoPlayer.ExoPlayerMessage(exoAudioRenderer, C.MSG_SET_VOLUME, volume));
        }
    }


    @SuppressWarnings("FieldCanBeLocal")
    private final ViewBackend.Callbacks backendViewCallbacks = new ViewBackend.Callbacks() {
        @Override
        public void onAvailable(ViewBackend backend) {
            sendSetSurfaceMessage(true, backend.getCurrentSurface());
        }

        @Override
        public void onSizeChanged(ViewBackend backend, int width, int height) {
        }

        @Override
        public void onDestroy(ViewBackend backend) {
            sendSetSurfaceMessage(true, null);
        }
    };

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                callbacks.onVideoBufferingStarts();
                break;

            case ExoPlayer.STATE_READY:
                // better re-apply volume state
                applyVolumeState();

                if (playWhenReady) {
                    callbacks.onVideoRenderingStarts();
                } else {
                    callbacks.onVideoBufferingEnds();
                }

                break;
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        // dont care!
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Throwable rootCause = Throwables.getRootCause(error);

        String messageChain = getMessageWithCauses(error);
        if (error.type == ExoPlaybackException.TYPE_SOURCE) {
            String message = context.getString(R.string.media_exo_error_io, rootCause.getMessage());
            callbacks.onVideoError(message, ErrorKind.NETWORK);

        } else if (messageChain.contains("Top bit not zero:")) {
            String message = context.getString(R.string.media_exo_error_topbit);
            callbacks.onVideoError(message, ErrorKind.NETWORK);

        } else {
            callbacks.onVideoError(messageChain, ErrorKind.UNKNOWN);

            // send to crashlytics, i want to have a look.
            AndroidUtility.logToCrashlytics(rootCause);
        }

        // try to reset the player
        pause();
    }

    @Override
    public void onPositionDiscontinuity() {
    }

    private static class VideoListener implements VideoRendererEventListener {
        private final WeakReference<VideoPlayer.Callbacks> callbacks;
        private final WeakReference<AspectLayout> parentView;

        VideoListener(Callbacks callbacks, AspectLayout parentView) {
            this.callbacks = new WeakReference<>(callbacks);
            this.parentView = new WeakReference<>(parentView);
        }

        @Override
        public void onVideoEnabled(DecoderCounters counters) {
        }

        @Override
        public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
            logger.info("Initialized decoder {} after {}ms", decoderName, initializationDurationMs);
        }

        @Override
        public void onVideoInputFormatChanged(Format format) {
            logger.info("Video format is now {}", format);
        }

        @Override
        public void onDroppedFrames(int count, long elapsed) {
            if (count >= MAX_DROPPED_FRAMES) {
                Callbacks callbacks = this.callbacks.get();
                if (callbacks != null) {
                    callbacks.onDroppedFrames(count);
                }
            }
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            if (width > 0 && height > 0) {
                int scaledWidth = (int) ((width * pixelWidthHeightRatio) + 0.5f);

                logger.info("Got video track with size {}x{}", scaledWidth, height);

                AspectLayout parentView = this.parentView.get();
                if (parentView != null) {
                    parentView.setAspect((float) scaledWidth / height);
                }

                Callbacks callbacks = this.callbacks.get();
                if (callbacks != null) {
                    callbacks.onVideoSizeChanged(scaledWidth, height);
                }
            }
        }

        @Override
        public void onRenderedFirstFrame(Surface surface) {
            Callbacks callbacks = this.callbacks.get();
            if (callbacks != null) {
                callbacks.onVideoRenderingStarts();
            }
        }

        @Override
        public void onVideoDisabled(DecoderCounters counters) {
        }
    }

    private static class MediaSourceListener implements ExtractorMediaSource.EventListener {
        private WeakReference<Callbacks> callbacks;

        MediaSourceListener(Callbacks callbacks) {
            this.callbacks = new WeakReference<>(callbacks);
        }

        @Override
        public void onLoadError(IOException error) {
            Callbacks callbacks = this.callbacks.get();
            if (callbacks != null) {
                callbacks.onVideoError(error.toString(), ErrorKind.NETWORK);
            }
        }
    }

    private static class MediaCodecSelectorImpl implements MediaCodecSelector {
        private final Settings settings;

        MediaCodecSelectorImpl(Settings settings) {
            this.settings = settings;
        }


        @Override
        public MediaCodecInfo getDecoderInfo(String mimeType, boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
            List<MediaCodecInfo> codecs = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);
            // logger.info("Codec selector for {} returned: {}", mimeType, Lists.transform(codecs, codec -> codec.name));

            // look fo the best matching codec to return to the user.
            String preference = mimeType.startsWith("video/") ? settings.videoCodec() : settings.audioCodec();
            return bestMatchingCodec(codecs, preference)
                    .or(Optional.fromNullable(codecs.size() > 0 ? codecs.get(0) : null))
                    .orNull();
        }

        @Override
        public MediaCodecInfo getPassthroughDecoderInfo() throws MediaCodecUtil.DecoderQueryException {
            return MediaCodecSelector.DEFAULT.getPassthroughDecoderInfo();
        }
    }

    ;

    static Optional<MediaCodecInfo> bestMatchingCodec(List<MediaCodecInfo> codecs, String videoCodecName) {
        if ("software".equals(videoCodecName)) {
            return from(codecs).firstMatch(ExoVideoPlayer::isSoftwareDecoder);

        } else if ("hardware".equals(videoCodecName)) {
            return from(codecs).firstMatch(ExoVideoPlayer::isHardwareDecoder);

        } else {
            return from(codecs).firstMatch(codec -> codec.name.equalsIgnoreCase(videoCodecName));
        }
    }

    private static boolean isSoftwareDecoder(MediaCodecInfo codec) {
        String name = codec.name.toLowerCase();
        return name.startsWith("omx.google.") || name.startsWith("omx.ffmpeg.") || name.contains(".sw.");
    }

    private static boolean isHardwareDecoder(MediaCodecInfo codec) {
        return !isSoftwareDecoder(codec);
    }

    private static class DataSourceFactory implements DataSource.Factory {
        private final Context context;
        private final Uri uri;

        public DataSourceFactory(Context context, Uri uri) {
            this.context = context;
            this.uri = uri;
        }

        @Override
        public DataSource createDataSource() {
            Cache cache = Dagger.appComponent(context).cache();
            return new InputStreamCacheDataSource(uri, cache);
        }
    }
}