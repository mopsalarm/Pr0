package com.pr0gramm.app.ui.fragments

import android.support.v7.widget.RecyclerView

/**
 * Scroll listener that does nothing
 */
internal class NoopOnScrollListener : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        // empty
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        // do nothing
    }
}
