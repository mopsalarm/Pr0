package com.pr0gramm.app.viewer;

import android.annotation.SuppressLint;
import android.content.Context;

import com.pr0gramm.app.R;

import rx.functions.Action1;

/**
 */
@SuppressLint("ViewConstructor")
public class ProxyMediaView extends MediaView {
    private MediaView child;

    private boolean started;
    private boolean resumed;
    private boolean playing;

    public ProxyMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_proxy, url);
    }

    public void setChild(MediaView viewer) {
        unsetChild();
        hideBusyIndicator();

        addView(child = viewer);
        bootupChild();
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
            if (started)
                child.onStart();

            if (resumed)
                child.onResume();

            if (playing)
                child.playMedia();
        }
    }

    private void teardownChild() {
        if (child != null) {
            if (playing)
                child.stopMedia();

            if (resumed)
                child.onPause();

            if (started)
                child.onStop();
        }
    }

    @Override
    public void onStart() {
        started = true;
        propagate(MediaView::onStart);
    }

    @Override
    public void onResume() {
        resumed = true;
        propagate(MediaView::onResume);
    }

    @Override
    public void playMedia() {
        playing = true;
        propagate(MediaView::playMedia);
    }

    @Override
    public void stopMedia() {
        playing = false;
        propagate(MediaView::stopMedia);
    }

    @Override
    public void onPause() {
        resumed = false;
        propagate(MediaView::onPause);
    }

    @Override
    public void onStop() {
        started = false;
        propagate(MediaView::onStop);
    }

    private void propagate(Action1<MediaView> action) {
        if (child != null)
            action.call(child);
    }
}
