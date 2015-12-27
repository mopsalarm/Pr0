package com.pr0gramm.app.ui.fragments;

import android.support.v7.widget.RecyclerView;

/**
 * Scroll listener that does nothing
 */
class NoopOnScrollListener extends RecyclerView.OnScrollListener {
    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        // empty
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        // do nothing
    }
}
