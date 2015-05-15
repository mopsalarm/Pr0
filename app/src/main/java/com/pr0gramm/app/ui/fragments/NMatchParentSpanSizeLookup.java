package com.pr0gramm.app.ui.fragments;

import android.support.v7.widget.GridLayoutManager;

/**
 */
public class NMatchParentSpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
    private final int skipItems;
    private final int spanCount;

    public NMatchParentSpanSizeLookup(int skipItems, int spanCount) {
        this.skipItems = skipItems;
        this.spanCount = spanCount;
    }

    @Override
    public int getSpanSize(int position) {
        return position >= skipItems ? 1 : spanCount;
    }

    @Override
    public int getSpanIndex(int position, int spanCount) {
        return position >= skipItems ? (position - skipItems) % spanCount : 0;
    }
}
