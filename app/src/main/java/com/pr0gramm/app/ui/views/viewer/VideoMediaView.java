package com.pr0gramm.app.ui.views.viewer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.akodiakson.sdk.simple.Sdk;
import com.google.common.base.Optional;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.views.viewer.video.VideoPlayer;
import com.pr0gramm.app.util.AndroidUtility;

import javax.inject.Inject;

import butterknife.BindView;

/**
 */
public class VideoMediaView extends AbstractProgressMediaView implements VideoPlayer.Callbacks {
    private static final String KEY_LAST_UNMUTED_VIDEO = "VideoMediaView.lastUnmutedVideo";

    @BindView(R.id.video)
    VideoPlayer videoPlayer;

    @BindView(R.id.mute)
    ImageView muteButtonView;

    @Inject
    SingleShotService singleShotService;

    @Inject
    Settings settings;

    @Inject
    SharedPreferences preferences;

    private boolean videoViewInitialized;

    protected VideoMediaView(Activity context, MediaUri mediaUri, Runnable onViewListener) {
        super(context,
                Sdk.isAtLeastJellyBean()
                        ? R.layout.player_kind_video_exo
                        : R.layout.player_kind_video_android,
                mediaUri, onViewListener);

        muteButtonView.setOnClickListener(v -> {
            setMuted(!videoPlayer.isMuted());
            Track.muted(!videoPlayer.isMuted());
        });

        RxView.detaches(this).subscribe(event -> videoPlayer.setVideoCallbacks(null));
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

            videoPlayer.setVideoCallbacks(this);
            videoPlayer.open(getEffectiveUri());
        }

        applyMuteState();
        videoPlayer.start();
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
            videoPlayer.setMuted(true);
        }
    }

    private void storeUnmuteTime(long time) {
        preferences.edit()
                .putLong(KEY_LAST_UNMUTED_VIDEO, time)
                .apply();
    }

    private void setMuted(boolean muted) {
        Drawable icon;

        videoPlayer.setMuted(muted);
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
    protected Optional<ProgressInfo> getVideoProgress() {
        if (videoPlayer != null && videoViewInitialized && isPlaying()) {
            return Optional.of(new ProgressInfo(videoPlayer.progress(), videoPlayer.buffered()));
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
        videoPlayer.pause();
    }

    @Override
    public void rewind() {
        videoPlayer.rewind();
    }


    @Override
    public void onVideoError(String message) {
        // we might be finished here :/
        hideBusyIndicator();

        DialogBuilder.start(getContext())
                .content(message)
                .positive()
                .show();
    }
}
