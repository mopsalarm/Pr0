package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import roboguice.inject.InjectView;

/**
 */
@SuppressLint("ViewConstructor")
public class ImageMediaView extends MediaView {
    @InjectView(R.id.image)
    private ImageView imageView;

    @Inject
    private Settings settings;

    @Inject
    private Picasso picasso;

    public ImageMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_image, url);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (imageView.getDrawable() == null) {
            RequestCreator requestCreator = picasso
                    .load(getUrlArgument())
                    .resize(1052, settings.maxImageSize())
                    .centerInside()
                    .onlyScaleDown();

            // disable fading if we don't use hardware acceleration.
            if (!settings.useHardwareAcceleration())
                requestCreator.noFade();

            requestCreator.into(imageView, new HideBusyIndicator(this));
        }
    }

    @Override
    public void onDestroy() {
        picasso.cancelRequest(imageView);
        imageView.setImageDrawable(null);

        ((ViewGroup) imageView.getParent()).removeView(imageView);

        super.onDestroy();
    }

    /**
     * We need to wrap the fragment into a weak reference so that the callback
     * will not create a memory leak.
     */
    private static class HideBusyIndicator implements Callback {
        private final WeakReference<ImageMediaView> fragment;

        public HideBusyIndicator(ImageMediaView fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void onSuccess() {
            ImageMediaView player = fragment.get();
            if (player != null)
                player.hideBusyIndicator();
        }

        @Override
        public void onError() {
            //  just indicate that we are finished.
            onSuccess();
        }
    }
}
