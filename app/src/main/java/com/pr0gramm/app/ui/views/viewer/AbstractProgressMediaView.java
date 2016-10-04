package com.pr0gramm.app.ui.views.viewer;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import com.google.common.base.Optional;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.util.AndroidUtility;

import javax.inject.Inject;

/**
 */
public abstract class AbstractProgressMediaView extends MediaView {
    @Inject
    Settings settings;

    boolean progressTouched = false;
    long lastUserInteraction = System.currentTimeMillis();

    private boolean progressEnabled = true;

    private final ProgressBar videoProgressView;

    AbstractProgressMediaView(Config config, @LayoutRes Integer layoutId, @LayoutRes int progressLayoutRes) {
        super(config, layoutId);

        videoProgressView = (ProgressBar) LayoutInflater
                .from(getContext())
                .inflate(progressLayoutRes, this, false);

        videoProgressView.setVisibility(GONE);

        if (videoProgressView instanceof SeekBar) {
            // let the user change the video position
            ((SeekBar) videoProgressView).setOnSeekBarChangeListener(seekbarChangeListener);
        }

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

    @Override
    protected boolean onSingleTap(MotionEvent event) {
        if (!currentlyVisible() && shouldAnimateSeekbar()) {
            lastUserInteraction = System.currentTimeMillis();
            animateToSize(AndroidUtility.dp(getContext(), 18));
        }

        return true;
    }

    private boolean currentlyVisible() {
        return videoProgressView.getPaddingLeft() > 0;
    }

    private boolean shouldAnimateSeekbar() {
        return videoProgressView instanceof SeekBar;
    }

    private void animateToSize(int targetValue) {
        int startValue = videoProgressView.getPaddingLeft();
        int minHeight = AndroidUtility.dp(getContext(), 2);

        Property<View, Integer> property = new Property<View, Integer>(Integer.class, "height") {
            @Override
            public Integer get(View object) {
                return object.getLayoutParams().height;
            }

            @Override
            public void set(View object, Integer value) {
                object.getLayoutParams().height = Math.max(minHeight, value);
                object.setPadding(value, getPaddingTop(), value, getPaddingBottom());
                object.requestLayout();
            }
        };

        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(videoProgressView,
                PropertyValuesHolder.ofInt(property, startValue, targetValue));

        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(300);
        animator.start();
    }

    private void updateTimeline() {
        if (!isPlaying())
            return;

        if (!progressTouched) {
            ProgressInfo info = getVideoProgress().orNull();
            if (info != null && shouldShowView(info)) {
                videoProgressView.setVisibility(VISIBLE);
                videoProgressView.setMax(1000);
                videoProgressView.setProgress((int) (1000 * info.progress));
                videoProgressView.setSecondaryProgress((int) (1000 * info.buffered));
            } else {
                videoProgressView.setVisibility(GONE);
            }

            if (shouldAnimateSeekbar() && currentlyVisible()
                    && lastUserInteraction > 0
                    && System.currentTimeMillis() - lastUserInteraction > 3000) {

                animateToSize(0);
                lastUserInteraction = -1;
            }
        }

        if (progressEnabled) {
            removeCallbacks(this::updateTimeline);
            postDelayed(this::updateTimeline, 200);
        }
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
