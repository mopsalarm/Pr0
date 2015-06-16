package com.pr0gramm.app.ui.views.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
import com.squareup.picasso.Target;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
@SuppressLint("ViewConstructor")
public class DelayedMediaView extends ProxyMediaView {
    private final View overlay;
    private final AtomicBoolean childCreated = new AtomicBoolean();
    private final Target previewTarget = new PreviewTarget(this);

    @Inject
    private Picasso picasso;

    public DelayedMediaView(Context context, Binder binder, String url, Runnable onViewListener) {
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
        picasso.cancelRequest(previewTarget);
    }

    @Override
    public void onTransitionEnds() {
        if (preview != null) {
            String url = getUrlArgument();
            String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
            Uri image = Uri.parse("http://pr0.wibbly-wobbly.de:5001/" + encoded + "/thumb.jpg");

            picasso.load(image).noPlaceholder().into(previewTarget);
        }
    }

    @Override
    protected boolean onSingleTap() {
        // call this function only exactly once!
        if (!childCreated.compareAndSet(false, true))
            return false;

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

        return true;
    }

    private static void removeViewFromParent(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    /**
     * Puts the loaded image into the preview container, if there
     * still is a preview container.
     */
    private static class PreviewTarget implements Target {
        private final WeakReference<DelayedMediaView> mediaView;

        public PreviewTarget(DelayedMediaView mediaView) {
            this.mediaView = new WeakReference<>(mediaView);
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            DelayedMediaView mediaView = this.mediaView.get();
            if (mediaView != null && mediaView.preview != null) {
                mediaView.preview.setImageBitmap(bitmap);
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    }
}
