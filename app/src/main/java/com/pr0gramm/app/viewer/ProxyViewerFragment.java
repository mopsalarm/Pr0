package com.pr0gramm.app.viewer;

import android.annotation.SuppressLint;
import android.content.Context;

import com.pr0gramm.app.R;

import rx.functions.Action1;

/**
 */
@SuppressLint("ViewConstructor")
public class ProxyViewerFragment extends ViewerFragment {
    private ViewerFragment child;

    private boolean started;
    private boolean resumed;
    private boolean playing;

    public ProxyViewerFragment(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_proxy, url);
    }

    public void setChild(ViewerFragment viewer) {
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
        propagate(ViewerFragment::onStart);
    }

    @Override
    public void onResume() {
        resumed = true;
        propagate(ViewerFragment::onResume);
    }

    @Override
    public void playMedia() {
        playing = true;
        propagate(ViewerFragment::playMedia);
    }

    @Override
    public void stopMedia() {
        playing = false;
        propagate(ViewerFragment::stopMedia);
    }

    @Override
    public void onPause() {
        resumed = false;
        propagate(ViewerFragment::onPause);
    }

    @Override
    public void onStop() {
        started = false;
        propagate(ViewerFragment::onStop);
    }

    private void propagate(Action1<ViewerFragment> action) {
        if (child != null)
            action.call(child);
    }
}
