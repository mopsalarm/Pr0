package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.media.MediaPlayer;

import com.google.inject.Inject;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.SingleShotService;

import roboguice.inject.InjectView;

/**
 */
public class VideoMediaView extends MediaView {
    @InjectView(R.id.video)
    private SimplifiedAndroidVideoView videoView;

    @Inject
    private SingleShotService singleShotService;

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

            // hide player at first
            videoView.setAlpha(0.01f);
        }


        // if this is the first time we start the media, tell the
        // user about the changes!
        if (singleShotService.isFirstTime("new_media_player_initialize_first_time")) {
            DialogBuilder.start(getContext())
                    .content("Der Videoplayer wurde überarbeitet. Falls Videos nicht mehr spielen, " +
                            "sende bitte *sofort* Feedback über die Seitenleiste. Als Workaround " +
                            "kannst du dann temporär den Softwaredecoder in den Einstellungen " +
                            "unter Kompatibilität aktivieren.")
                    .positive(R.string.okay)
                    .show();
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
            }
        }

        return true;
    }

    private void onMediaPlayerPrepared(MediaPlayer player) {
        player.setLooping(true);
        videoView.setAlpha(1.f);
        hideBusyIndicator();

        if(isPlaying()) {
            // mark media as viewed
            onMediaShown();
        }
    }
}
