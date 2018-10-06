package com.pr0gramm.app.ui.fragments

/**
 * Scroll listener that does nothing
 */
internal class NoopOnScrollListener : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
        // empty
    }

    override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
        // do nothing
    }
}
