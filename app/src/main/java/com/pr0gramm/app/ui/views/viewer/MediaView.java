package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
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
import com.pr0gramm.app.ui.fragments.PostFragment;
import com.pr0gramm.app.ui.views.AspectImageView;
import com.squareup.picasso.Picasso;

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
    private boolean mediaShown;

    protected final Binder binder;
    protected final String url;

    private Runnable onViewListener;

    private TapListener tapListener;
    private boolean started;
    private boolean resumed;
    private boolean playing;

    @Nullable
    private View progress;

    @Nullable
    protected AspectImageView preview;

    @Inject
    private Settings settings;

    @Inject
    private Picasso picasso;

    private float viewAspect = -1;

    protected MediaView(Context context, Binder binder, @LayoutRes Integer layoutId, String url,
                        Runnable onViewListener) {

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

            if (progressId != null)
                progress = findViewById(progressId);

            preview = (AspectImageView) findViewById(R.id.preview);
        } else {
            preview = null;
        }

        RoboInjector injector = RoboGuice.getInjector(context);
        injector.injectMembersWithoutViews(this);
        injector.injectViewMembers(this);

        // register the detector to handle double taps
        gestureDetector = new GestureDetector(context, gestureListener);

        showBusyIndicator();
    }

    /**
     * Sets the preview image for this media view. You need to provide a width and height.
     * Those values will be used to place the preview image correctly.
     */
    public void setPreviewImage(PostFragment.PreviewInfo info, String transitionName) {
        if (preview != null) {
            if (info.getWidth() > 0 && info.getHeight() > 0) {
                float aspect = (float) info.getWidth() / (float) info.getHeight();
                preview.setAspect(aspect);
                setViewAspect(aspect);
            }

            if (info.getPreview() != null) {
                preview.setImageDrawable(info.getPreview());

            } else if (info.getPreviewUri() != null) {
                // quickly load the preview into this view
                picasso.load(info.getPreviewUri()).into(preview);

            } else {
                // no preview for this item, remove the view
                removePreviewImage();
            }

            ViewCompat.setTransitionName(preview, transitionName);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (viewAspect <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        } else {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = (int) (width / viewAspect) + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);

            measureChildren(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    /**
     * Removes the preview drawable.
     */
    public void removePreviewImage() {
        if (this.preview != null) {
            // cancel loading of preview, if there is still a request pending.
            picasso.cancelRequest(preview);

            AndroidUtility.removeView(preview);
            this.preview = null;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    /**
     * This method must be called after a shared element
     * transition for the preview image ends.
     */
    public void onTransitionEnds() {
        // do nothing, override as needed
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
            return MediaView.this.onDoubleTap();
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onSingleTap();
        }
    };

    protected boolean onDoubleTap() {
        return tapListener != null && tapListener.onDoubleTap();

    }

    protected boolean onSingleTap() {
        return tapListener != null && tapListener.onSingleTap();
    }

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

    public TapListener getTapListener() {
        return tapListener;
    }

    public void setTapListener(TapListener tapListener) {
        this.tapListener = tapListener;
    }

    /**
     * Sets the aspect ratio of this view. Will be ignored, if not positive and size
     * is then estimated from the children. If aspect is provided, the size of
     * the view is estimated from its parents width.
     */
    public void setViewAspect(float viewAspect) {
        if (this.viewAspect != viewAspect) {
            this.viewAspect = viewAspect;
            requestLayout();
        }
    }

    public float getViewAspect() {
        return viewAspect;
    }

    /**
     * Returns the url that this view should display.
     */
    protected String getUrlArgument() {
        return url;
    }

    public void playMedia() {
        logger.info("Should start playing media");
        playing = true;

        if (mediaShown) {
            onMediaShown();
        }
    }

    public void stopMedia() {
        logger.info("Should stop playing media");
        playing = false;
    }

    protected void onMediaShown() {
        removePreviewImage();

        if (isPlaying() && onViewListener != null) {
            onViewListener.run();
            onViewListener = null;
        }
    }

    public interface Binder {
        <T> Observable<T> bind(Observable<T> observable);
    }

    public interface TapListener {
        boolean onSingleTap();

        boolean onDoubleTap();
    }

    public static class TapListenerAdapter implements TapListener {
        @Override
        public boolean onSingleTap() {
            return false;
        }

        @Override
        public boolean onDoubleTap() {
            return false;
        }

    }

    private static final LayoutParams DEFAULT_PARAMS = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
}
