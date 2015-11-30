package com.pr0gramm.app.ui.views.viewer;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.widget.ProgressBar;

import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import javax.inject.Inject;

import butterknife.Bind;

/**
 */
public abstract class AbstractProgressMediaView extends MediaView {
    @Bind(R.id.video_progress)
    ProgressBar videoProgressView;

    @Inject
    Settings settings;

    private boolean progressEnabled = true;

    public AbstractProgressMediaView(Activity activity, @LayoutRes Integer layoutId, MediaUri mediaUri, Runnable onViewListener) {
        super(activity, layoutId, mediaUri, onViewListener);

        videoProgressView.setVisibility(GONE);
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

        Optional<Float> progress = getVideoProgress();
        if (shouldShowView(progress)) {
            videoProgressView.setVisibility(VISIBLE);
            videoProgressView.setProgress((int) (1000 * progress.get()));
            videoProgressView.setMax(1000);
        } else {
            videoProgressView.setVisibility(GONE);
        }

        if (progressEnabled) {
            removeCallbacks(this::updateTimeline);
            postDelayed(this::updateTimeline, 200);
        }
    }

    private boolean shouldShowView(Optional<Float> progress) {
        return progressEnabled && progress.isPresent() && progress.get() >= 0 && progress.get() <= 1;
    }

    protected abstract Optional<Float> getVideoProgress();

    public void setProgressEnabled(boolean visible) {
        progressEnabled = visible;
    }
}
