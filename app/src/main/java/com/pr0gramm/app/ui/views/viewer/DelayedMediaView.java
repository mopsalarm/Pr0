package com.pr0gramm.app.ui.views.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.pr0gramm.app.R;

/**
 */
@SuppressLint("ViewConstructor")
public class DelayedMediaView extends ProxyMediaView {
    private final View playNow;

    public DelayedMediaView(Context context, Binder binder, String url) {
        super(context, binder, url);
        hideBusyIndicator();

        playNow = LayoutInflater.from(context).inflate(R.layout.play_now, this, false);
        playNow.setOnClickListener(v -> playNow());
        addView(playNow);
    }

    private void playNow() {
        removeView(playNow);
        setChild(MediaViews.newInstance(getContext(), binder, url));

        addView(playNow);

        playNow.animate()
                .alpha(0).scaleX(0.8f).scaleY(0.8f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeView(playNow);
                    }
                }).start();
    }
}
