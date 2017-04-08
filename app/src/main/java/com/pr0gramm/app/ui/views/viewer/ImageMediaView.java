package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.ErrorFormatting;
import com.pr0gramm.app.util.decoders.Decoders;
import com.pr0gramm.app.util.decoders.PicassoDecoder;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import butterknife.BindView;

/**
 */
@SuppressLint("ViewConstructor")
public class ImageMediaView extends MediaView {
    private static final Logger logger = LoggerFactory.getLogger("ImageMediaView");

    // we cap the image if it is more than 30 times as high as it is wide
    private static final float CAP_IMAGE_RATIO = 1.f / 30.f;

    private final String tag = "ImageMediaView" + System.identityHashCode(this);
    private final boolean zoomView;

    @BindView(R.id.image)
    SubsamplingScaleImageView imageView;

    @BindView(R.id.error)
    TextView errorIndicator;

    @Inject
    Picasso picasso;

    @Inject
    Downloader downloader;

    @Inject
    SingleShotService singleShotService;

    ImageMediaView(Config config) {
        super(config, R.layout.player_kind_image);

        zoomView = findViewById(R.id.tabletlayout) != null;

        if (zoomView) {
            logger.info("Media view has a zoomview now");
        }

        imageView.setVisibility(VISIBLE);
        imageView.setAlpha(0.f);
        imageView.setDebug(BuildConfig.DEBUG);
        imageView.setZoomEnabled(zoomView);

        // try not to use too much memory, even on big devices
        imageView.setMaxTileSize(2048);

        imageView.setBitmapDecoderFactory(() -> new PicassoDecoder(tag, picasso));
        imageView.setRegionDecoderFactory(() -> Decoders.newFancyRegionDecoder(downloader));
        imageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override
            public void onImageLoaded() {
                hideBusyIndicator();
                onMediaShown();
            }

            @Override
            public void onImageLoadError(Exception error) {
                hideBusyIndicator();
                showErrorIndicator(error);
            }

            @Override
            public void onReady() {
                applyScaling();
            }
        });

        // re-apply scaling after layout.
        RxView.layoutChanges(imageView)
                .filter(event -> imageView.getSWidth() > 0 && imageView.getSHeight() > 0)
                .subscribe(event -> applyScaling());

        RxView.detaches(this).subscribe(event -> {
            picasso.cancelTag(tag);
            imageView.recycle();
            imageView.setOnImageEventListener(null);
            AndroidUtility.removeView(imageView);
        });

        // start loading
        boolean tiling = config.previewInfo()
                .transform(info -> info.getWidth() > 2000 || info.getHeight() > 2000)
                .or(false);

        imageView.setImage(ImageSource.uri(getEffectiveUri()).tiling(tiling));

        showBusyIndicator();
    }

    void applyScaling() {
        float ratio = imageView.getSWidth() / (float) imageView.getSHeight();
        float ratioCapped = Math.max(ratio, CAP_IMAGE_RATIO);

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
            super.setViewAspect(viewAspect);
        }
    }

    @Override
    protected void onMediaShown() {
        imageView.setVisibility(VISIBLE);

        if (imageView.getAlpha() == 0) {
            imageView.animate().alpha(1)
                    .setDuration(ANIMATION_DURATION)
                    .setListener(AndroidUtility.endAction(super::onMediaShown))
                    .start();
        } else {
            super.onMediaShown();
        }
    }

    @SuppressLint("SetTextI18n")
    void showErrorIndicator(Exception error) {
        errorIndicator.setVisibility(VISIBLE);
        errorIndicator.setAlpha(0);
        errorIndicator.animate().alpha(1).start();

        // set a more useful error message
        errorIndicator.setText(getContext().getText(R.string.could_not_load_image)
                + "\n\n" + ErrorFormatting.INSTANCE.getFormatter(error).getMessage(getContext(), error));
    }
}
