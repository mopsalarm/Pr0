package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.media.MediaPlayer;

import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.ui.views.viewer.video.CustomVideoView;

import roboguice.inject.InjectView;

/**
 */
public class VideoMediaView extends MediaView {
    @InjectView(R.id.video)
    private CustomVideoView videoView;

    @Inject
    private SingleShotService singleShotService;

    @Inject
    private Settings settings;

    private int retryCount;
    private boolean videoViewInitialized;

    protected VideoMediaView(Context context, Binder binder,
                             MediaUri mediaUri, Runnable onViewListener) {

        super(context, binder, R.layout.player_simple_video_view, mediaUri, onViewListener);
    }

    @Override
    public void playMedia() {
        super.playMedia();

        if (!videoViewInitialized) {
            videoViewInitialized = true;
            videoView.setVideoURI(getEffectiveUri());
            videoView.setOnPreparedListener(this::onMediaPlayerPrepared);
            videoView.setOnErrorListener(this::onMediaPlayerError);
            videoView.setOnVideoSizeChangedListener(this::onVideoSizeChanged);
            videoView.setOnInfoListener(this::onVideoInfoEvent);
        }


        // if this is the first time we start the media, tell the
        // user about the changes!
        if (!settings.useSoftwareDecoder() && singleShotService.isFirstTimeInVersion("hint_check_compatibility_settings")) {
            DialogBuilder.start(getContext())
                    .content(R.string.hint_check_compatibility_if_videos_dont_play)
                    .positive(R.string.okay)
                    .show();
        }
        videoView.start();
    }

    private boolean onVideoInfoEvent(MediaPlayer mediaPlayer, int event, int i) {
        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            showBusyIndicator();
        }

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            hideBusyIndicator();
        }

        return false;
    }

    private void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
        setViewAspect(width / (float) height);
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        videoView.pause();
    }

    @Override
    public void rewind() {
        videoView.seekTo(0);
    }

    private boolean onMediaPlayerError(MediaPlayer mediaPlayer, int what, int extra) {
        final int METHOD_CALLED_IN_INVALID_STATE = -38;

        logger.error("media player error occurred: {} {}", what, extra);

        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED
                || what == METHOD_CALLED_IN_INVALID_STATE
                || extra == METHOD_CALLED_IN_INVALID_STATE) {

            if (retryCount < 3) {
                retryCount++;

                // try again in a moment
                if (isPlaying()) {
                    postDelayed(() -> {
                        if (isPlaying()) {
                            videoView.resume();
                        }
                    }, 500);
                }

                return true;
            }
        }

        try {
            if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == MediaPlayer.MEDIA_ERROR_IO) {
                DialogBuilder.start(getContext())
                        .content(R.string.could_not_play_video_io)
                        .positive(R.string.okay)
                        .show();

                return true;
            }

            DialogBuilder.start(getContext())
                    .content(R.string.could_not_play_video)
                    .positive(R.string.okay)
                    .show();

        } catch (Exception ignored) {
        }

        return true;
    }


    private void onMediaPlayerPrepared(MediaPlayer player) {
        player.setLooping(true);
        hideBusyIndicator();

        if (isPlaying()) {
            // mark media as viewed
            onMediaShown();
        }
    }
}
