package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.ImageDecoders;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import butterknife.Bind;

/**
 */
@SuppressLint("ViewConstructor")
public class ImageMediaView extends MediaView {
    private static final Logger logger = LoggerFactory.getLogger("ImageMediaView");

    private final String tag = "ImageMediaView" + System.identityHashCode(this);
    private final boolean zoomView;
    private boolean viewInitialized;

    @Bind(R.id.image)
    SubsamplingScaleImageView imageView;

    @Bind(R.id.error)
    View errorIndicator;

    @Inject
    Settings settings;

    @Inject
    Picasso picasso;

    @Inject
    Downloader downloader;

    @Inject
    SingleShotService singleShotService;


    public ImageMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, R.layout.player_image, url, onViewListener);

        zoomView = findViewById(R.id.tabletlayout) != null;

        if (isZoomView()) {
            logger.info("Media view has a zoomview now");
        }

        imageView.setDebug(BuildConfig.DEBUG);
        imageView.setZoomEnabled(isZoomView());

        imageView.setBitmapDecoderFactory(() -> new ImageDecoders.PicassoDecoder(tag, picasso));
        imageView.setRegionDecoderFactory(() -> new ImageDecoders.PicassoRegionDecoder(downloader));
        imageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override
            public void onImageLoaded() {
                hideBusyIndicator();
                onMediaShown();
            }

            @Override
            public void onImageLoadError(Exception e) {
                hideBusyIndicator();
                showErrorIndicator();
            }

            @Override
            public void onReady() {
                float ratio = imageView.getSWidth() / (float) imageView.getSHeight();
                float ratioCapped = Math.max(ratio, 1 / 10.f);

                setViewAspect(ratioCapped);

                float maxScale = imageView.getWidth() / (float) imageView.getSWidth();
                float minScale = isZoomView()
                        ? imageView.getHeight() / (float) imageView.getSHeight()
                        : maxScale * (ratio / ratioCapped);

                imageView.setMinScale(minScale);
                imageView.setMaxScale(maxScale);
                imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);
            }
        });
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void setViewAspect(float viewAspect) {
        if (!isZoomView()) {
            super.setViewAspect(viewAspect);
        }
    }

    private boolean isZoomView() {
        return zoomView;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!viewInitialized) {
            imageView.setImage(ImageSource.uri(getEffectiveUri()));
            viewInitialized = true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        picasso.cancelTag(tag);
        ((ViewGroup) imageView.getParent()).removeView(imageView);
    }

    /**
     * We need to wrap the fragment into a weak reference so that the callback
     * will not create a memory leak.
     */
//    private static class ImageCallback implements Callback {
//        private final WeakReference<ImageMediaView> fragment;
//
//        public ImageCallback(ImageMediaView fragment) {
//            this.fragment = new WeakReference<>(fragment);
//        }
//
//        @Override
//        public void onSuccess() {
//            ImageMediaView player = fragment.get();
//            if (player != null) {
//                Drawable drawable = player.imageView.getDrawable();
//                float imageAspect = (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
//                player.setViewAspect(imageAspect);
//
//                if (player.isZoomView()) {
//                    showZoomableImage(player, drawable);
//                }
//
//                player.hideBusyIndicator();
//                player.onMediaShown();
//
//                if (drawable.getIntrinsicHeight() / (float) drawable.getIntrinsicWidth() > 3) {
//                    player.showMussteScrollenPopup();
//                }
//            }
//        }
//
//        @Override
//        public void onError() {
//            ImageMediaView player = fragment.get();
//            if (player != null) {
//                player.hideBusyIndicator();
//                player.showErrorIndicator();
//            }
//        }
//    }

//    private static void showZoomableImage(ImageMediaView player, Drawable drawable) {
//        ImageViewTouch imageView = (ImageViewTouch) player.imageView;
//
//        float viewAspect = (float) imageView.getWidth() / imageView.getHeight();
//        float imageAspect = (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
//
//        float maxScale = Math.max(1.00001f, viewAspect / imageAspect);
//        imageView.setImageDrawable(drawable, null, 1, maxScale);
//        imageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);
//
//        if (atLeast(Build.VERSION_CODES.KITKAT)) {
//            imageView.setQuickScaleEnabled(true);
//        }
//    }
    private void showErrorIndicator() {
        errorIndicator.setVisibility(VISIBLE);
        errorIndicator.setAlpha(0);
        errorIndicator.animate().alpha(1).start();
    }

    private void showMussteScrollenPopup() {
        if (singleShotService.isFirstTime("musste_scrollen_hint")) {
            DialogBuilder.start(getContext())
                    .content(R.string.hint_musste_scrollen)
                    .positive()
                    .show();
        }
    }
}
