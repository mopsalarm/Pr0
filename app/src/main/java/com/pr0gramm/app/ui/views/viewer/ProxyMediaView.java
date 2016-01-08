package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;

import com.pr0gramm.app.R;

import rx.functions.Action1;

/**
 */
@SuppressLint("ViewConstructor")
public abstract class ProxyMediaView extends MediaView {
    private MediaView child;

    public ProxyMediaView(Activity context, MediaUri uri, Runnable onViewListener) {
        super(context, R.layout.player_proxy, uri, onViewListener);
    }

    public void setChild(MediaView child) {
        removeChildView();
        hideBusyIndicator();

        setChildView(child);

        // disable view aspect, let the child determine our size
        setViewAspect(-1);

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
            if (isStarted())
                child.onStart();

            if (isResumed())
                child.onResume();

            if (isPlaying())
                child.playMedia();

            if (hasTransitionEnded())
                child.onTransitionEnds();
        }
    }

    private void teardownChild() {
        if (child != null) {
            if (isPlaying())
                child.stopMedia();

            if (isResumed())
                child.onPause();

            if (isStarted())
                child.onStop();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        propagate(MediaView::onStart);
    }

    @Override
    public void onResume() {
        super.onResume();
        propagate(MediaView::onResume);
    }

    @Override
    public void playMedia() {
        super.playMedia();
        propagate(MediaView::playMedia);
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        propagate(MediaView::stopMedia);
    }

    @Override
    public void rewind() {
        propagate(MediaView::rewind);
    }

    @Override
    public MediaView getActualMediaView() {
        return child != null ? child : this;
    }

    @Override
    public void onPause() {
        propagate(MediaView::onPause);
        super.onPause();
    }

    @Override
    public void onStop() {
        propagate(MediaView::onStop);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        propagate(MediaView::onDestroy);
        super.onDestroy();
    }

    @Override
    public void onTransitionEnds() {
        super.onTransitionEnds();
        propagate(MediaView::onTransitionEnds);
    }

    private void propagate(Action1<MediaView> action) {
        if (child != null)
            action.call(child);
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
