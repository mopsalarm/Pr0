package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;

import javax.annotation.Nullable;

import roboguice.RoboGuice;
import roboguice.inject.InjectView;
import roboguice.inject.RoboInjector;
import rx.Observable;

import static android.view.GestureDetector.SimpleOnGestureListener;
import static java.lang.System.identityHashCode;

/**
 */
public abstract class MediaView extends FrameLayout {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    protected final String TAG = getClass().getSimpleName() + " " + Integer.toString(
            identityHashCode(this), Character.MAX_RADIX);

    private final GestureDetector gestureDetector;
    protected final Binder binder;
    protected final String url;

    private OnDoubleTapListener onDoubleTapListener;
    private boolean started;
    private boolean resumed;
    private boolean playing;

    @Nullable
    @InjectView(R.id.progress)
    private View progress;

    public MediaView(Context context, Binder binder, @LayoutRes Integer layoutId, String url) {
        super(context);
        this.binder = binder;
        this.url = url;

        setLayoutParams(DEFAULT_PARAMS);
        if (layoutId != null)
            LayoutInflater.from(context).inflate(layoutId, this);

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
        if (progress != null)
            progress.setVisibility(View.VISIBLE);
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
                progress = null;
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
     * Instantiates one of the viewer fragments subclasses depending
     * on the provided url.
     *
     * @param url The url that should be displayed.
     * @return A new {@link MediaView} instance.
     */
    public static MediaView newInstance(Context context, Binder binder, String url) {
        MediaView result;
        Settings settings = Settings.of(context);
        if (url.toLowerCase().endsWith(".webm")) {
            boolean useCompatVideoPlayer = settings.useCompatVideoPlayer();

            // we use a api that is not available in ICS, so we need to
            // fallback to the compat player before jelly bean.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                useCompatVideoPlayer = true;

            // FIXME Always force compat player.
            useCompatVideoPlayer = true;

            result = useCompatVideoPlayer
                    ? new SimpleVideoMediaView(context, binder, url)
                    : new VideoMediaView(context, binder, url);

        } else if (url.toLowerCase().endsWith(".gif")) {
            if (settings.convertGifToWebm()) {
                result = new Gif2WebmMediaView(context, binder, url);
            } else {
                result = new GifMediaView(context, binder, url);
            }

        } else {
            result = new ImageMediaView(context, binder, url);
        }

        return result;
    }

    /**
     * Creates a new {@link com.pr0gramm.app.ui.views.viewer.MediaView} instance
     * for the given feed item.
     *
     * @param context  The current context
     * @param feedItem The feed item that is to be displayed.
     * @return A new {@link com.pr0gramm.app.ui.views.viewer.MediaView} instance.
     */
    public static MediaView newInstance(Context context, Binder binder, FeedItem feedItem) {
        String url = "http://img.pr0gramm.com/" + feedItem.getImage();
        return newInstance(context, binder, url);
    }

    /**
     * Resizes the video view while keeping the given aspect ratio.
     */
    protected void resizeViewerView(View view, float aspect, int retries) {
        Log.i(TAG, "Setting aspect of viewer View to " + aspect);

        if (view.getWindowToken() == null) {
            if (retries > 0) {
                Log.i(TAG, "Delay resizing of View for 100ms");
                HANDLER.postDelayed(() -> resizeViewerView(view, aspect, retries - 1), 100);
            }

            return;
        }

        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            int parentWidth = ((View) parent).getWidth();
            if (parentWidth == 0) {
                // relayout again in a short moment
                if (retries > 0) {
                    Log.i(TAG, "Delay resizing of View for 100ms");
                    HANDLER.postDelayed(() -> resizeViewerView(view, aspect, retries - 1), 100);
                }

                return;
            }

            int newHeight = (int) (parentWidth / aspect);
            if (view.getHeight() == newHeight) {
                Log.i(TAG, "View already correctly sized at " + parentWidth + "x" + newHeight);
                return;
            }

            Log.i(TAG, "Setting size of View to " + parentWidth + "x" + newHeight);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = newHeight;
            view.setLayoutParams(params);

        } else {
            Log.w(TAG, "View has no parent, can not set size.");
        }
    }

    public void playMedia() {
        Log.i(TAG, "Should start playing media");
        playing = true;
    }

    public void stopMedia() {
        Log.i(TAG, "Should stop playing media");
        playing = false;
    }

    public interface Binder {
        <T> Observable<T> bind(Observable<T> observable);

        void onError(String text);
    }

    public interface OnDoubleTapListener {
        void onDoubleTap();
    }

    private static final LayoutParams DEFAULT_PARAMS = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
}
