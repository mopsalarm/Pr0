package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectExtra;
import roboguice.inject.InjectView;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;

public class ZoomViewActivity extends RoboActionBarActivity {
    private static final Logger logger = LoggerFactory.getLogger(ZoomViewActivity.class);

    @InjectExtra("ZoomViewActivity.imageUrl")
    private String imageUrl;

    @InjectView(R.id.image)
    private ImageViewTouch imageView;

    @InjectView(R.id.busy_indicator)
    private View busyIndicator;

    @Inject
    private Picasso picasso;

    @Inject
    private Settings settings;

    // a handler for this activity
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // normal content view
        setContentView(R.layout.activity_zoom_view);
        imageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);

        // additional canvas view to estimate maximum image size
        addTemporaryCanvasView();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && settings.fullscreenZoomView()) {
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            decorView.setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onDestroy() {
        picasso.cancelRequest(imageView);
        super.onDestroy();
    }

    public static Intent newIntent(Context context, String imageUrl) {
        Intent intent = new Intent(context, ZoomViewActivity.class);
        intent.putExtra("ZoomViewActivity.imageUrl", "http://img.pr0gramm.com/" + imageUrl);
        return intent;
    }

    private void loadImage(int maximumBitmapWidth, int maximumBitmapHeight) {
        picasso.load(imageUrl)
                .resize(maximumBitmapWidth, maximumBitmapHeight)
                .onlyScaleDown()
                .centerInside()
                .into(imageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        checkMainThread();
                        removeBusyIndicator();
                    }

                    @Override
                    public void onError() {
                        checkMainThread();
                        if (!isFinishing() && getWindow() != null) {
                            ErrorDialogFragment.showErrorString(
                                    getSupportFragmentManager(),
                                    "Could not load the image in full size");
                        }
                    }
                });
    }

    private void removeBusyIndicator() {
        busyIndicator.setVisibility(View.GONE);

        ViewParent parent = busyIndicator.getParent();
        if(parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(busyIndicator);
        }
    }

    /**
     * Adds a temporary canvas view to estimate the maximum texture size.
     */
    private void addTemporaryCanvasView() {
        // adds the canvas view.
        addContentView(new CanvasView(this), new ViewGroup.LayoutParams(1, 1));
    }

    /**
     * Tries to remove the given canvas view from the activity.
     */
    private void removeCanvasView(CanvasView view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        } else {
            logger.warn("Could not remove temporary view canvas!");
        }
    }

    private class CanvasView extends View {
        private final AtomicBoolean drawCalled = new AtomicBoolean();

        public CanvasView(Context context) {
            super(context);

            setWillNotDraw(false);
            setDrawingCacheEnabled(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawCalled.compareAndSet(false, true)) {
                // only perform the loading on  ce
                onCanvasAvailable(canvas);
            }
        }

        private void onCanvasAvailable(Canvas canvas) {
            int maximumBitmapWidth = Math.max(2048, canvas.getMaximumBitmapWidth());
            int maximumBitmapHeight = Math.max(2048, canvas.getMaximumBitmapHeight());
            logger.info("Maximum image size is " + maximumBitmapWidth + "x" + maximumBitmapHeight);

            // now start loading the image and remove this view again
            handler.post(() -> {
                loadImage(maximumBitmapWidth, maximumBitmapHeight);
                removeCanvasView(this);
            });
        }
    }
}
