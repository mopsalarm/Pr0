package com.pr0gramm.app.ui.views.viewer;

import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.widget.ProgressBar;

import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import javax.inject.Inject;

/**
 */
public abstract class AbstractProgressMediaView extends MediaView {
    ProgressBar videoProgressView;

    @Inject
    Settings settings;

    private boolean progressEnabled = true;

    AbstractProgressMediaView(Config config, @LayoutRes Integer layoutId) {
        super(config, layoutId);


        videoProgressView = (ProgressBar) LayoutInflater
                .from(getContext())
                .inflate(R.layout.player_video_progress, this, false);

        videoProgressView.setVisibility(GONE);

        publishControllerView(videoProgressView);

        updateTimeline();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTimeline();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        updateTimeline();
    }

    private void updateTimeline() {
        if (!isPlaying())
            return;

        Optional<ProgressInfo> info = getVideoProgress();
        if (shouldShowView(info)) {
            videoProgressView.setVisibility(VISIBLE);
            videoProgressView.setMax(1000);
            videoProgressView.setProgress((int) (1000 * info.get().progress));
            videoProgressView.setSecondaryProgress((int) (1000 * info.get().buffered));
        } else {
            videoProgressView.setVisibility(GONE);
        }

        if (progressEnabled) {
            removeCallbacks(this::updateTimeline);
            postDelayed(this::updateTimeline, 200);
        }
    }

    private boolean shouldShowView(Optional<ProgressInfo> info) {
        if (progressEnabled && info.isPresent()) {
            ProgressInfo theInfo = info.get();
            return (theInfo.progress >= 0 && theInfo.progress <= 1
                    || theInfo.buffered >= 0 && theInfo.buffered <= 1);
        } else {
            return false;
        }
    }

    protected abstract Optional<ProgressInfo> getVideoProgress();

    public void setProgressEnabled(boolean visible) {
        progressEnabled = visible;
    }

    protected static class ProgressInfo {
        public final float progress;
        public final float buffered;

        public ProgressInfo(float progress, float buffered) {
            this.buffered = buffered;
            this.progress = progress;
        }
    }
}
