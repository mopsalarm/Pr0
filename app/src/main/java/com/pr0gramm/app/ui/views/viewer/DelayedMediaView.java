package com.pr0gramm.app.ui.views.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.squareup.picasso.Picasso;

/**
 */
@SuppressLint("ViewConstructor")
public class DelayedMediaView extends ProxyMediaView {
    private final View overlay;
    private final View playNow;
    private final ImageView thumbnail;

    @Inject
    private Picasso picasso;

    public DelayedMediaView(Context context, Binder binder, String url) {
        super(context, binder, url);
        hideBusyIndicator();

        overlay = LayoutInflater.from(context).inflate(R.layout.play_now, this, false);
        playNow = overlay.findViewById(R.id.play_now);
        playNow.setOnClickListener(v -> playNow());

        thumbnail = (ImageView) overlay.findViewById(R.id.thumbnail);
        loadThumbnailImage();

        addView(overlay);
    }

    @Override
    public void addView(@NonNull View child) {
        // prepend children, don't append them
        super.addView(child, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        picasso.cancelRequest(thumbnail);
    }

    private void loadThumbnailImage() {
        String url = getUrlArgument();
        if (!url.endsWith(".webm"))
            return;

        String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
        Uri image = Uri.parse("http://pr0.wibbly-wobbly.de:5001/" + encoded + "/thumb.jpg");
        picasso.load(image).into(thumbnail);
    }

    private void playNow() {
        setChild(MediaViews.newInstance(getContext(), binder, url));

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
        if(parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }
}
