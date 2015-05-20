package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;

import roboguice.RoboGuice;
import roboguice.inject.RoboInjector;
import rx.Observable;

import static android.view.GestureDetector.SimpleOnGestureListener;

/**
 */
public abstract class MediaView extends FrameLayout {
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName()
            + " " + Integer.toHexString(System.identityHashCode(this)));

    private final GestureDetector gestureDetector;
    protected final Binder binder;
    protected final String url;
    protected final Runnable onViewListener;

    private OnDoubleTapListener onDoubleTapListener;
    private boolean started;
    private boolean resumed;
    private boolean playing;

    @Nullable
    private View progress;

    @Inject
    private Settings settings;

    protected MediaView(Context context, Binder binder, @LayoutRes Integer layoutId, String url, Runnable onViewListener) {
        this(context, binder, layoutId, R.id.progress, url, onViewListener);
    }

    protected MediaView(Context context, Binder binder,
                        @LayoutRes Integer layoutId, @IdRes Integer progressId,
                        String url, Runnable onViewListener) {

        super(context);
        this.binder = binder;
        this.url = url;
        this.onViewListener = onViewListener;

        setLayoutParams(DEFAULT_PARAMS);
        if (layoutId != null) {
            LayoutInflater.from(context).inflate(layoutId, this);
            setMinimumHeight(AndroidUtility.dp(context, 150));

            if (progressId != null)
                progress = findViewById(progressId);
        }

        RoboInjector injector = RoboGuice.getInjector(context);
        injector.injectMembersWithoutViews(this);
        injector.injectViewMembers(this);

        // register the detector to handle double taps
        gestureDetector = new GestureDetector(context, gestureListener);

        showBusyIndicator();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    /**
     * The listener that handles double tapping
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (onDoubleTapListener != null) {
                onDoubleTapListener.onDoubleTap();
                return true;
            }

            return false;
        }
    };

    /**
     * Displays an indicator that something is loading. You need to pair
     * this with a call to {@link #hideBusyIndicator()}
     */
    protected void showBusyIndicator() {
        if (progress != null) {
            if (progress.getParent() == null)
                addView(progress);

            progress.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hides the busy indicator that was shown in {@link #showBusyIndicator()}.
     */
    protected void hideBusyIndicator() {
        if (progress != null) {
            progress.setVisibility(View.GONE);

            ViewParent parent = progress.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(progress);
            }
        }
    }

    @Nullable
    public View getProgressView() {
        return progress;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isResumed() {
        return resumed;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void onStart() {
        started = true;
    }

    public void onStop() {
        started = false;
    }

    public void onPause() {
        resumed = false;
    }

    public void onResume() {
        resumed = true;
    }

    public void onDestroy() {
        if (playing)
            stopMedia();

        if (resumed)
            onPause();

        if (started)
            onStop();
    }

    public OnDoubleTapListener getOnDoubleTapListener() {
        return onDoubleTapListener;
    }

    public void setOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener) {
        this.onDoubleTapListener = onDoubleTapListener;
    }

    /**
     * Returns the url that this view should display.
     */
    protected String getUrlArgument() {
        return url;
    }

    /**
     * Resizes the video view while keeping the given aspect ratio.
     */
    protected void resizeViewerView(View view, float aspect, int retries) {
        if (Float.isNaN(aspect)) {
            logger.info("Not setting aspect to NaN!");
            return;
        }

        logger.info("Setting aspect of viewer View to " + aspect);

        if (view.getWindowToken() == null) {
            if (retries > 0) {
                logger.info("Delay resizing of View for 100ms");
                postDelayed(() -> resizeViewerView(view, aspect, retries - 1), 100);
            }

            return;
        }

        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            int parentWidth = ((View) parent).getWidth();
            if (parentWidth == 0) {
                // relayout again in a short moment
                if (retries > 0) {
                    logger.info("Delay resizing of View for 100ms");
                    postDelayed(() -> resizeViewerView(view, aspect, retries - 1), 100);
                }

                return;
            }

            int newHeight = (int) (parentWidth / aspect);
            if (view.getHeight() == newHeight) {
                logger.info("View already correctly sized at " + parentWidth + "x" + newHeight);
                return;
            }

            logger.info("Setting size of View to " + parentWidth + "x" + newHeight);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = newHeight;
            view.setLayoutParams(params);

        } else {
            logger.warn("View has no parent, can not set size.");
        }
    }

    public void playMedia() {
        logger.info("Should start playing media");
        playing = true;
    }

    public void stopMedia() {
        logger.info("Should stop playing media");
        playing = false;
    }

    public interface Binder {
        <T> Observable<T> bind(Observable<T> observable);
    }

    public interface OnDoubleTapListener {
        void onDoubleTap();
    }

    private static final LayoutParams DEFAULT_PARAMS = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
}
