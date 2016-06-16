package com.pr0gramm.app.ui.views.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.views.viewer.video.VideoPlayer;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import butterknife.BindView;

/**
 */
public class VideoMediaView extends AbstractProgressMediaView implements VideoPlayer.Callbacks {
    private static final Logger logger = LoggerFactory.getLogger("VideoMediaView");
    private static final String KEY_LAST_UNMUTED_VIDEO = "VideoMediaView.lastUnmutedVideo";

    @BindView(R.id.video)
    VideoPlayer videoView;

    @BindView(R.id.mute)
    ImageView muteButtonView;

    @Inject
    SingleShotService singleShotService;

    @Inject
    Settings settings;

    @Inject
    SharedPreferences preferences;

    private boolean videoViewInitialized;

    // only show error once.
    private boolean shouldShowIoError = true;
    private int retryCount;

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

            videoView.setVideoCallbacks(this);
            videoView.open(getEffectiveUri());


//            videoView.setVideoURI(getEffectiveUri());
//            videoView.setOnPreparedListener(this::onMediaPlayerPrepared);
//            videoView.setOnErrorListener(this::onMediaPlayerError);
//            videoView.setOnVideoSizeChangedListener(this::onVideoSizeChanged);
//            videoView.setOnInfoListener(this::onVideoInfoEvent);
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
            float progress = videoView.progress();

            if (progress >= 0 && progress <= 1) {
                return Optional.of(progress);
            }
        }

        return Optional.absent();
    }

    @Override
    public void onVideoBufferingStarts() {
        showBusyIndicator();
    }

    @Override
    public void onVideoBufferingEnds() {
        hideBusyIndicator();
    }

    @Override
    public void onVideoRenderingStarts() {
        if (isPlaying()) {
            // mark media as viewed
            onMediaShown();
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        setViewAspect(width / (float) height);
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        videoView.pause();
    }

    @Override
    public void rewind() {
        videoView.rewind();
    }


    @Override
    public void onVideoError(String message) {
        DialogBuilder.start(getContext())
                .content(message)
                .positive()
                .show();
    }
}
