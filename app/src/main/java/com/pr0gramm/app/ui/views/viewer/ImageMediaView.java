package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import butterknife.Bind;
import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;

import static com.pr0gramm.app.util.AndroidUtility.atLeast;

/**
 */
@SuppressLint("ViewConstructor")
public class ImageMediaView extends MediaView {
    private static final Logger logger = LoggerFactory.getLogger("ImageMediaView");

    @Bind(R.id.image)
    ImageView imageView;

    @Bind(R.id.error)
    View errorIndicator;

    @Inject
    Settings settings;

    @Inject
    Picasso picasso;

    @Inject
    SingleShotService singleShotService;

    public ImageMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, R.layout.player_image, url, onViewListener);

        if (isZoomView()) {
            logger.info("Media view has a zoomview now");
        }
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
        return imageView instanceof ImageViewTouch;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (imageView.getDrawable() == null) {
            picasso.load(getEffectiveUri())
                    .resize(settings.maxImageSize(), settings.maxImageSize())
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
                Drawable drawable = player.imageView.getDrawable();
                float imageAspect = (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
                player.setViewAspect(imageAspect);

                if (player.isZoomView()) {
                    showZoomableImage(player, drawable);
                }

                player.hideBusyIndicator();
                player.onMediaShown();

                if (drawable.getIntrinsicHeight() / (float) drawable.getIntrinsicWidth() > 3) {
                    player.showMussteScrollenPopup();
                }
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

    private static void showZoomableImage(ImageMediaView player, Drawable drawable) {
        ImageViewTouch imageView = (ImageViewTouch) player.imageView;

        float viewAspect = (float) imageView.getWidth() / imageView.getHeight();
        float imageAspect = (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();

        float maxScale = Math.max(1.00001f, viewAspect / imageAspect);
        imageView.setImageDrawable(drawable, null, 1, maxScale);
        imageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);

        if (atLeast(Build.VERSION_CODES.KITKAT)) {
            imageView.setQuickScaleEnabled(true);
        }
    }

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
