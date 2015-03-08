package com.pr0gramm.app.viewer;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pr0gramm.app.NestingFragment;
import com.pr0gramm.app.R;

import roboguice.inject.InjectView;

/**
 */
public abstract class ViewerFragment extends NestingFragment {
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
    public static ViewerFragment newInstance(String url) {
        ViewerFragment result;
        if (url.toLowerCase().endsWith(".webm")) {
            result = new VideoViewerFragment();
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
}
