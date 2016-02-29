package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

import com.pr0gramm.app.R;

/**
 */
@SuppressLint("ViewConstructor")
public abstract class ProxyMediaView extends MediaView {
    private MediaView child;

    public ProxyMediaView(Activity context, MediaUri uri, Runnable onViewListener) {
        super(context, R.layout.player_proxy, uri, onViewListener);
        showBusyIndicator();
    }

    public void setChild(MediaView child) {
        removeChildView();
        hideBusyIndicator();

        setChildView(child);

        bootupChild();

        // forward double clicks
        child.setTapListener(new ForwardingTapListener());
    }

    /**
     * Adds the proxied child above the preview.
     */
    private void setChildView(MediaView mediaView) {
        int idx = getChildCount();
        View previewView = getPreviewView();
        if (previewView != null && previewView.getParent() == this) {
            idx = indexOfChild(previewView) + 1;
        }

        // transfer the layout parameters
        mediaView.setLayoutParams(getLayoutParams());
        mediaView.setViewAspect(getViewAspect());
        addView(mediaView, idx);

        child = mediaView;
    }

    private void removeChildView() {
        if (child == null)
            return;

        teardownChild();
        removeView(child);

        child = null;
    }

    private void bootupChild() {
        if (child != null) {
            if (isResumed())
                child.onResume();

            if (isPlaying())
                child.playMedia();
        }
    }

    private void teardownChild() {
        if (child != null) {
            if (isPlaying())
                child.stopMedia();

            if (isResumed())
                child.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (child != null)
            child.onResume();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        if (child != null)
            child.playMedia();
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        if (child != null)
            child.stopMedia();
    }

    @Override
    public void rewind() {
        if (child != null)
            child.rewind();
    }

    @Override
    public MediaView getActualMediaView() {
        return child != null ? child : this;
    }

    @Override
    protected void onMediaShown() {
        setViewAspect(-1);
        removePreviewImage();
        super.onMediaShown();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (child != null)
            child.onPause();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (child != null) {
            event.offsetLocation(
                    child.getPaddingLeft() - getPaddingLeft(),
                    child.getPaddingTop() - getPaddingTop());

            return child.onTouchEvent(event);
        }

        return super.onTouchEvent(event);
    }

    private class ForwardingTapListener implements TapListener {
        @Override
        public boolean onSingleTap() {
            return getTapListener() != null && getTapListener().onSingleTap();
        }

        @Override
        public boolean onDoubleTap() {
            return getTapListener() != null && getTapListener().onDoubleTap();
        }
    }
}
