package com.pr0gramm.app.viewer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.pr0gramm.app.NestingFragment;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import roboguice.inject.InjectView;

import static java.lang.System.identityHashCode;

/**
 */
public abstract class ViewerFragment extends NestingFragment {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    protected final String TAG = getClass().getSimpleName() + " " + Integer.toString(
            identityHashCode(this), Character.MAX_RADIX);

    @Nullable
    @InjectView(R.id.progress)
    private View progress;

    @LayoutRes
    private final int layoutId;

    public ViewerFragment(@LayoutRes int layoutId) {
        this.layoutId = layoutId;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showBusyIndicator();
    }

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
        if (progress != null)
            progress.setVisibility(View.GONE);
    }

    /**
     * Returns the url that this view should display.
     */
    protected String getUrlArgument() {
        return getArguments().getString("url");
    }

    /**
     * Instantiates one of the viewer fragments subclasses depending
     * on the provided url.
     *
     * @param url The url that should be displayed.
     * @return A new {@link ViewerFragment} instance.
     */
    public static ViewerFragment newInstance(Settings settings, String url) {
        ViewerFragment result;
        if (url.toLowerCase().endsWith(".webm")) {
            result = settings.useCompatVideoPlayer()
                    ? new VideoCompatViewerFragment()
                    : new VideoViewerFragment();

        } else if (url.toLowerCase().endsWith(".gif")) {
            result = new GifViewerFragment();

        } else {
            result = new ImageViewerFragment();
        }

        // add url to fragment arguments
        Bundle arguments = new Bundle();
        arguments.putString("url", url);
        result.setArguments(arguments);
        return result;
    }


    /**
     * Resizes the video view while keeping the given aspect ratio.
     */
    protected void resizeViewerView(View view, float aspect, int retries) {
        Log.i(TAG, "Setting aspect of viewer View to " + aspect);

        if (view.getWindowToken() == null)
            return;

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

}
