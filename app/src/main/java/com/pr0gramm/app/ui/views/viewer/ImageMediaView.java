package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
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

    @Inject
    private Settings settings;

    @Inject
    private Picasso picasso;

    public ImageMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_image, url);

        picasso.load(getUrlArgument())
                .resize(1024, settings.maxImageSize())
                .centerInside()
                .onlyScaleDown()
                .into(imageView, new HideBusyIndicator(this));
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
