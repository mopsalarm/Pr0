package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;

import com.pr0gramm.app.R;

import rx.functions.Action1;

/**
 */
@SuppressLint("ViewConstructor")
public class ProxyMediaView extends MediaView {
    private MediaView child;

    public ProxyMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_proxy, url);
    }

    public void setChild(MediaView viewer) {
        unsetChild();
        hideBusyIndicator();

        addView(child = viewer);
        bootupChild();

        // forward double clicks
        child.setOnDoubleTapListener(() -> {
            OnDoubleTapListener listener = getOnDoubleTapListener();
            if (listener != null)
                listener.onDoubleTap();
        });
    }

    public void unsetChild() {
        showBusyIndicator();
        if (child == null)
            return;

        teardownChild();
        removeView(child);
    }

    private void bootupChild() {
        if (child != null) {
            if (isStarted())
                child.onStart();

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

    private void propagate(Action1<MediaView> action) {
        if (child != null)
            action.call(child);
    }
}
