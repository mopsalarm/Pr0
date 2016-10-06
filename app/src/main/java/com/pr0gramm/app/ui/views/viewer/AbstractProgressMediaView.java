package com.pr0gramm.app.ui.views.viewer;

import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static com.pr0gramm.app.util.AndroidUtility.endAction;

/**
 */
public abstract class AbstractProgressMediaView extends MediaView {
    private static final Logger logger = LoggerFactory.getLogger("AbstractProgressMediaView");

    @Inject
    Settings settings;

    boolean progressTouched = false;
    long lastUserInteraction = -1;

    private boolean progressEnabled = true;
    private boolean firstTimeProgressValue = true;

    private final SeekBar seekBarView;
    private final ProgressBar progressView;

    AbstractProgressMediaView(Config config, @LayoutRes Integer layoutId) {
        super(config, layoutId);

        progressView = (ProgressBar) LayoutInflater
                .from(getContext())
                .inflate(R.layout.player_video_progress, this, false);

        seekBarView = (SeekBar) LayoutInflater
                .from(getContext())
                .inflate(R.layout.player_video_seekbar, this, false);

        publishControllerView(progressView);
        publishControllerView(seekBarView);
        updateTimeline();

        seekBarView.setOnSeekBarChangeListener(seekbarChangeListener);
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

    @Override
    protected boolean onSingleTap(MotionEvent event) {
        if (userSeekable() && !seekCurrentlyVisible()) {
            logger.info("Show seekbar after tap.");

            lastUserInteraction = System.currentTimeMillis();
            showSeekbar(true);
            return true;
        }

        return super.onSingleTap(event);
    }

    protected boolean userSeekable() {
        return false;
    }

    private boolean seekCurrentlyVisible() {
        return seekBarView.getVisibility() == VISIBLE;
    }

    private void showSeekbar(boolean show) {
        int deltaY = AndroidUtility.dp(getContext(), 12);

        View viewToShow = show ? seekBarView : progressView;
        View viewToHide = show ? progressView : seekBarView;

        if (viewToHide.getVisibility() == VISIBLE) {
            viewToHide.setTranslationY(0);
            viewToHide.animate()
                    .alpha(0)
                    .translationY(deltaY)
                    .setListener(endAction(() -> viewToHide.setVisibility(GONE)))
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }


        if (viewToShow.getVisibility() != VISIBLE) {
            viewToShow.setAlpha(0);
            viewToShow.setTranslationY(deltaY);
            viewToShow.setVisibility(VISIBLE);
            viewToShow.animate()
                    .alpha(1)
                    .translationY(0)
                    .setListener(null)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void updateTimeline() {
        if (!isPlaying())
            return;

        if (!progressTouched) {
            ProgressInfo info = getVideoProgress().orNull();
            if (info != null && shouldShowView(info)) {
                if (firstTimeProgressValue) {
                    firstTimeProgressValue = false;
                    progressView.setVisibility(VISIBLE);
                    progressView.setAlpha(1);
                    progressView.setTranslationY(0);
                }

                for (ProgressBar view : new ProgressBar[]{progressView, seekBarView}) {
                    view.setMax(1000);
                    view.setProgress((int) (1000 * info.progress));
                    view.setSecondaryProgress((int) (1000 * info.buffered));
                }

                if (userSeekable() && seekHideTimeoutReached()) {
                    logger.info("Hiding seekbar after idle timeout");
                    lastUserInteraction = -1;
                    showSeekbar(false);
                }
            } else {
                lastUserInteraction = -1;
                firstTimeProgressValue = true;
                seekBarView.setVisibility(GONE);
                progressView.setVisibility(GONE);
            }
        }

        if (progressEnabled) {
            removeCallbacks(this::updateTimeline);
            postDelayed(this::updateTimeline, 200);
        }
    }

    private boolean seekHideTimeoutReached() {
        return seekCurrentlyVisible()
                && lastUserInteraction > 0
                && System.currentTimeMillis() - lastUserInteraction > 3000;
    }

    private boolean shouldShowView(@NonNull ProgressInfo info) {
        return progressEnabled
                && (info.progress >= 0 && info.progress <= 1
                || info.buffered >= 0 && info.buffered <= 1);
    }

    protected abstract Optional<ProgressInfo> getVideoProgress();

    /**
     * Implement to seek after user input.
     */
    protected void userSeekTo(float fraction) {
    }

    public void setProgressEnabled(boolean visible) {
        progressEnabled = visible;

        if (!visible) {
            lastUserInteraction = -1;
            firstTimeProgressValue = true;
            seekBarView.setVisibility(GONE);
            progressView.setVisibility(GONE);
        }
    }

    protected static class ProgressInfo {
        final float progress;
        final float buffered;

        ProgressInfo(float progress, float buffered) {
            this.buffered = buffered;
            this.progress = progress;
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final SeekBar.OnSeekBarChangeListener seekbarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                lastUserInteraction = System.currentTimeMillis();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            progressTouched = true;
            lastUserInteraction = System.currentTimeMillis();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int currentValue = seekBar.getProgress();
            userSeekTo(currentValue / (float) seekBar.getMax());

            progressTouched = false;
            lastUserInteraction = System.currentTimeMillis();
        }
    };
}
