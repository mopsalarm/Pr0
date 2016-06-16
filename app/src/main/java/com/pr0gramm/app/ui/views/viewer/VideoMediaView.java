package com.pr0gramm.app.ui.views.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.Stats;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.views.viewer.video.CustomVideoView;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import butterknife.BindView;

/**
 */
public class VideoMediaView extends AbstractProgressMediaView {
    private static final Logger logger = LoggerFactory.getLogger("VideoMediaView");
    private static final String KEY_LAST_UNMUTED_VIDEO = "VideoMediaView.lastUnmutedVideo";

    @BindView(R.id.video)
    CustomVideoView videoView;

    @BindView(R.id.mute)
    ImageView muteButtonView;

    @Inject
    SingleShotService singleShotService;

    @Inject
    Settings settings;

    @Inject
    SharedPreferences preferences;

    private int retryCount;
    private boolean videoViewInitialized;

    // only show error once.
    private boolean shouldShowIoError = true;

    protected VideoMediaView(Activity context, MediaUri mediaUri, Runnable onViewListener) {
        super(context, R.layout.player_simple_video_view, mediaUri, onViewListener);

        muteButtonView.setOnClickListener(v -> {
            setMuted(!videoView.isMuted());
            Track.muted(!videoView.isMuted());
        });
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void playMedia() {
        super.playMedia();

        if (!videoViewInitialized) {
            showBusyIndicator();

            videoViewInitialized = true;
            videoView.setVideoURI(getEffectiveUri());
            videoView.setOnPreparedListener(this::onMediaPlayerPrepared);
            videoView.setOnErrorListener(this::onMediaPlayerError);
            videoView.setOnVideoSizeChangedListener(this::onVideoSizeChanged);
            videoView.setOnInfoListener(this::onVideoInfoEvent);
        }

        applyMuteState();
        videoView.start();
    }

    private AppCompatActivity getParentActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof AppCompatActivity) {
                return (AppCompatActivity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public void setHasAudio(boolean hasAudio) {
        super.setHasAudio(hasAudio);
        muteButtonView.setVisibility(hasAudio ? VISIBLE : GONE);
    }

    /**
     * Mute if not "unmuted" within the last 10 minutes.
     */
    private void applyMuteState() {
        if (hasAudio()) {
            long now = System.currentTimeMillis();
            long lastUnmutedVideo = preferences.getLong(KEY_LAST_UNMUTED_VIDEO, 0);
            long diff = (now - lastUnmutedVideo) / 1000;
            setMuted(diff > 10 * 60);
        } else {
            videoView.setMuted(true);
        }
    }

    private void storeUnmuteTime(long time) {
        preferences.edit()
                .putLong(KEY_LAST_UNMUTED_VIDEO, time)
                .apply();
    }

    private void setMuted(boolean muted) {
        Drawable icon;

        videoView.setMuted(muted);
        if (muted) {
            storeUnmuteTime(0);

            icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_volume_off_white_24dp);
        } else {
            storeUnmuteTime(System.currentTimeMillis());

            icon = AndroidUtility.getTintentDrawable(getContext(),
                    R.drawable.ic_volume_up_white_24dp, ThemeHelper.primaryColor());
        }

        muteButtonView.setImageDrawable(icon);
    }

    @Override
    protected boolean onSingleTap(MotionEvent event) {
        if (hasAudio()) {
            Rect rect = new Rect();
            muteButtonView.getHitRect(rect);

            boolean contained = rect.contains((int) event.getX(), (int) event.getY());
            if (contained) {
                muteButtonView.performClick();
                return true;
            }
        }

        return super.onSingleTap(event);
    }


    @Override
    protected Optional<Float> getVideoProgress() {
        if (videoView != null && videoViewInitialized && isPlaying()) {
            int position = videoView.getCurrentPosition();
            int duration = videoView.getDuration();

            if (position >= 0 && duration > 0) {
                return Optional.of(position / (float) duration);
            }
        }

        return Optional.absent();
    }

    private boolean onVideoInfoEvent(MediaPlayer mediaPlayer, int event, int i) {
        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            showBusyIndicator();
        }

        if (event == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            hideBusyIndicator();
        }

        if (event == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            Stats.get().increment("video.playback.succeeded");
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

        if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == MediaPlayer.MEDIA_ERROR_IO) {
            if (shouldShowIoError) {
                DialogBuilder.start(getContext())
                        .content(R.string.could_not_play_video_io)
                        .positive()
                        .show();

                shouldShowIoError = false;
            }

            hideBusyIndicator();
            return true;
        }

        try {
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

            DialogBuilder.start(getContext())
                    .content(errorMessage)
                    .positive()
                    .show();

            Stats.get().incrementCounter("video.playback.failed", "reason:" + errorKey);


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
