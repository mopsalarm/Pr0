package com.pr0gramm.app.ui.views;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;

import java.util.List;

import static android.animation.ObjectAnimator.ofPropertyValuesHolder;
import static android.util.FloatMath.cos;

/**
 */
public class BusyCircleView extends FrameLayout {
    private ImmutableList<Animator> animators;

    public BusyCircleView(Context context) {
        super(context);
        init();
    }

    public BusyCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BusyCircleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.busy_circles, this);

        List<View> circles = ImmutableList.of(
                findViewById(R.id.first),
                findViewById(R.id.second),
                findViewById(R.id.third));


        ImmutableList.Builder<Animator> animators = ImmutableList.builder();
        for (int idx = 0; idx < circles.size(); idx++) {
            float delta = idx / (float) circles.size();

            ObjectAnimator animator = ofPropertyValuesHolder(circles.get(idx),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.f, 0.0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.f, 0.0f));

            animator.setDuration(1000);
            animator.setInterpolator(buildInterpolator(delta / 4));
            animator.setRepeatMode(ObjectAnimator.RESTART);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animators.add(animator);
        }

        this.animators = animators.build();
    }

    private void startAnimation() {
        if (animators == null)
            return;

        for (Animator animator : animators) {
            if (animator.isRunning())
                continue;

            animator.setupStartValues();
            animator.start();
        }
    }

    private void stopAnimation() {
        if (animators == null)
            return;

        for (Animator animator : animators)
            animator.cancel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private static TimeInterpolator buildInterpolator(float delta) {
        return value -> {
            float result = cos((value - delta) * 2 * (float) Math.PI) / 2 + 0.5f;
            return result * result;
        };
    }
}
