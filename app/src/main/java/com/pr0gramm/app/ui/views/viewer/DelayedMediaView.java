package com.pr0gramm.app.ui.views.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;
import com.squareup.picasso.Picasso;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

/**
 */
@SuppressLint("ViewConstructor")
public class DelayedMediaView extends ProxyMediaView {
    private final View overlay;
    private final AtomicBoolean childCreated = new AtomicBoolean();

    @Inject
    Picasso picasso;

    public DelayedMediaView(Activity context, Binder binder, MediaUri url, Runnable onViewListener) {
        super(context, binder, url, onViewListener);
        hideBusyIndicator();

        overlay = LayoutInflater.from(context).inflate(R.layout.player_delayed_overlay, this, false);

        // Display the overlay in a smooth animation
        overlay.setAlpha(0);
        overlay.setScaleX(0.8f);
        overlay.setScaleY(0.8f);
        overlay.animate()
                .alpha(1).scaleX(1).scaleY(1)
                .setStartDelay(300).start();

        addView(overlay);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    protected boolean onSingleTap() {
        // call this function only exactly once!
        if (!childCreated.compareAndSet(false, true))
            return false;

        // create the real view as a child.
        MediaView mediaView = MediaViews.newInstance((Activity) getContext(),
                binder, mediaUri.withDelay(false), this::onMediaShown);

        mediaView.removePreviewImage();
        setChild(mediaView);

        // transfer the current height from the thumbnail to the video.
        setMinimumHeight(overlay.getMeasuredHeight());

        overlay.animate()
                .alpha(0).scaleX(0.8f).scaleY(0.8f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        AndroidUtility.removeView(overlay);
                    }
                }).start();

        return true;
    }
}
