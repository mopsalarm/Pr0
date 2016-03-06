package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.ui.ImageDecoders;
import com.pr0gramm.app.util.AndroidUtility;
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

    // we cap the image if it is more than 15 times as high as it is wide
    private static final float CAP_IMAGE_RATIO = 1.f / 15.f;

    private final String tag = "ImageMediaView" + System.identityHashCode(this);
    private final boolean zoomView;

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
        super(context, R.layout.player_image, url.withProxy(false), onViewListener);

        zoomView = findViewById(R.id.tabletlayout) != null;

        if (zoomView) {
            logger.info("Media view has a zoomview now");
        }

        imageView.setVisibility(VISIBLE);
        imageView.setAlpha(0.f);
        imageView.setDebug(BuildConfig.DEBUG);
        imageView.setZoomEnabled(zoomView);

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
                cacheMediaSize(imageView.getSWidth(), imageView.getSHeight());
                applyScaling();
            }
        });

        // re-apply scaling after layout.
        RxView.layoutChanges(imageView)
                .filter(event -> imageView.getSWidth() > 0 && imageView.getSHeight() > 0)
                .subscribe(event -> applyScaling());

        RxView.detaches(this).subscribe(event -> {
            picasso.cancelTag(tag);
            AndroidUtility.removeView(imageView);
        });

        // start loading
        imageView.setImage(ImageSource.uri(getEffectiveUri()));
        showBusyIndicator();
    }

    private void applyScaling() {
        float ratio = imageView.getSWidth() / (float) imageView.getSHeight();
        float ratioCapped = Math.max(ratio, CAP_IMAGE_RATIO);
        if (ratio < CAP_IMAGE_RATIO) {
            removeBlurredBackground();
        }

        setViewAspect(ratioCapped);

        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float minScale, maxScale;
        if (zoomView) {
            maxScale = viewWidth / imageView.getSWidth();
            minScale = viewHeight / imageView.getSHeight();
        } else {
            minScale = maxScale = (viewWidth / imageView.getSWidth()) * (ratio / ratioCapped);
        }

        logger.info("scaling: {}, {}", minScale, maxScale);
        imageView.setMinScale(minScale);
        imageView.setMaxScale(maxScale);
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);
        imageView.resetScaleAndCenter();
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void setViewAspect(float viewAspect) {
        if (!zoomView) {
            if (viewAspect < CAP_IMAGE_RATIO)
                removeBlurredBackground();

            super.setViewAspect(viewAspect);
        }
    }

    @Override
    protected void onMediaShown() {
        imageView.setVisibility(VISIBLE);

        if (imageView.getAlpha() == 0) {
            imageView.setAlpha(0.f);
            imageView.animate().alpha(1)
                    .setDuration(ANIMATION_DURATION)
                    .setListener(AndroidUtility.endAction(super::onMediaShown))
                    .start();
        } else {
            super.onMediaShown();
        }
    }

    private void showErrorIndicator() {
        errorIndicator.setVisibility(VISIBLE);
        errorIndicator.setAlpha(0);
        errorIndicator.animate().alpha(1).start();
    }
}
