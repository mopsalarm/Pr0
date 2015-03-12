package com.pr0gramm.app;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.google.common.collect.ImmutableList;

import static android.animation.ObjectAnimator.ofPropertyValuesHolder;
import static android.util.FloatMath.cos;

/**
 */
public class BusyCircleView extends FrameLayout {
    private ImmutableList<View> circles;

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

        this.circles = ImmutableList.of(
                findViewById(R.id.first),
                findViewById(R.id.second),
                findViewById(R.id.third));

        for (int idx = 0; idx < circles.size(); idx++) {
            float delta = idx / (float) circles.size();

            ObjectAnimator animator = ofPropertyValuesHolder(circles.get(idx),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.f, 0.0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.f, 0.0f));

            animator.setDuration(1000);
            animator.setInterpolator(buildInterpolator(delta / 2));
            animator.setRepeatMode(ObjectAnimator.RESTART);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.start();
        }
    }

    private static TimeInterpolator buildInterpolator(float delta) {
        return value -> {
            float result = cos((value - delta) * 2 * (float) Math.PI) / 2 + 0.5f;
            return result * result;
        };
    }
}
