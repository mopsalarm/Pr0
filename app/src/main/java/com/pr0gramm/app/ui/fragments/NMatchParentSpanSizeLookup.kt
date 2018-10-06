package com.pr0gramm.app.ui.fragments

/**
 */
class NMatchParentSpanSizeLookup(private val skipItems: Int, private val spanCount: Int) :
        androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {

    override fun getSpanSize(position: Int): Int {
        return if (position >= skipItems) 1 else spanCount
    }

    override fun getSpanIndex(position: Int, spanCount: Int): Int {
        return if (position >= skipItems) (position - skipItems) % spanCount else 0
    }
}
