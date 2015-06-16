package com.pr0gramm.app.ui.views.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.squareup.picasso.Picasso;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
@SuppressLint("ViewConstructor")
public class DelayedMediaView extends ProxyMediaView {
    private final View overlay;

    @Inject
    private Picasso picasso;

    private AtomicBoolean childCreated = new AtomicBoolean();

    public DelayedMediaView(Context context, Binder binder, String url, Runnable onViewListener) {
        super(context, binder, url, onViewListener);
        hideBusyIndicator();

        overlay = LayoutInflater.from(context).inflate(R.layout.player_delayed_overlay, this, false);
        overlay.setOnClickListener(v -> playNow());

        // Display the overlay in a smooth animation
        overlay.setAlpha(0);
        overlay.setScaleX(0.8f);
        overlay.setScaleY(0.8f);
        overlay.animate()
                .alpha(1).scaleX(1).scaleY(1)
                .setStartDelay(300).start();

        addView(overlay);

        loadThumbnailImage();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (preview != null) {
            // stop request if still loading
            picasso.cancelRequest(preview);
        }
    }

    private void loadThumbnailImage() {
        if (preview != null) {
            String url = getUrlArgument();
            String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
            Uri image = Uri.parse("http://pr0.wibbly-wobbly.de:5001/" + encoded + "/thumb.jpg");
            picasso.load(image).into(preview);
        }
    }

    private void playNow() {
        // call this function only exactly once!
        if (!childCreated.compareAndSet(false, true))
            return;

        // create the real view as a child.
        MediaView mediaView = MediaViews.newInstance(getContext(), binder, url, onViewListener);
        mediaView.removePreviewImage();
        setChild(mediaView);

        // transfer the current height from the thumbnail to the video.
        setMinimumHeight(overlay.getMeasuredHeight());

        overlay.animate()
                .alpha(0).scaleX(0.8f).scaleY(0.8f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeViewFromParent(overlay);
                    }
                }).start();
    }

    private static void removeViewFromParent(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }
}
