package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.media.MediaPlayer;

import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

/**
 */
public class SimpleVideoMediaView extends MediaView {
    @InjectView(R.id.video)
    private SimplifiedAndroidVideoView videoView;

    int retryCount;
    boolean videoViewInitialized;

    protected SimpleVideoMediaView(Context context, Binder binder,
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

            // hide player at first
            videoView.setAlpha(0.01f);
        }

        videoView.start();
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        videoView.pause();
    }

    private boolean onMediaPlayerError(MediaPlayer mediaPlayer, int what, int extra) {
        final int METHOD_CALLED_IN_INVALID_STATE = -38;

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
            }
        }

        return true;
    }

    private void onMediaPlayerPrepared(MediaPlayer player) {
        player.setLooping(true);
        videoView.setAlpha(1.f);
        hideBusyIndicator();
    }
}
