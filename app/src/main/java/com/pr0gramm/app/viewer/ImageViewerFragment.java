package com.pr0gramm.app.viewer;

import android.os.Bundle;
import android.view.View;
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
public class ImageViewerFragment extends ViewerFragment {
    @InjectView(R.id.image)
    private ImageView imageView;

    @Inject
    private Settings settings;

    @Inject
    private Picasso picasso;

    public ImageViewerFragment() {
        super(R.layout.player_image);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
        private final WeakReference<ImageViewerFragment> fragment;

        public HideBusyIndicator(ImageViewerFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void onSuccess() {
            ImageViewerFragment player = fragment.get();
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
