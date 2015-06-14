package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import roboguice.inject.InjectView;

/**
 */
@SuppressLint("ViewConstructor")
public class ImageMediaView extends MediaView {
    @InjectView(R.id.image)
    private ImageView imageView;

    @InjectView(R.id.error)
    private View errorIndicator;

    @Inject
    private Settings settings;

    @Inject
    private Picasso picasso;

    public ImageMediaView(Context context, Binder binder, String url, Runnable onViewListener) {
        super(context, binder, R.layout.player_image, url, onViewListener);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (imageView.getDrawable() == null) {
            picasso.load(getUrlArgument())
                    .resize(1052, settings.maxImageSize())
                    .centerInside()
                    .onlyScaleDown()
                    .into(imageView, new ImageCallback(this));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        picasso.cancelRequest(imageView);
        imageView.setImageDrawable(null);

        ((ViewGroup) imageView.getParent()).removeView(imageView);
    }

    /**
     * We need to wrap the fragment into a weak reference so that the callback
     * will not create a memory leak.
     */
    private static class ImageCallback implements Callback {
        private final WeakReference<ImageMediaView> fragment;

        public ImageCallback(ImageMediaView fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void onSuccess() {
            ImageMediaView player = fragment.get();
            if (player != null) {
                player.hideBusyIndicator();
                player.onViewListener.run();
            }
        }

        @Override
        public void onError() {
            ImageMediaView player = fragment.get();
            if (player != null) {
                player.hideBusyIndicator();
                player.showErrorIndicator();
            }
        }
    }

    private void showErrorIndicator() {
        errorIndicator.setVisibility(VISIBLE);
        errorIndicator.setAlpha(0);
        errorIndicator.animate().alpha(1).start();
    }
}
