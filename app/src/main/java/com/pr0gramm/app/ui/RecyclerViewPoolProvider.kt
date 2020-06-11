package com.pr0gramm.app.ui

import androidx.recyclerview.widget.RecyclerView

interface RecyclerViewPoolProvider {
    fun recyclerViewPool(key: String): RecyclerView.RecycledViewPool

    fun invalidateRecyclerViewPool() {
        // implement if supported
    }
}

class RecyclerViewPoolMap : RecyclerViewPoolProvider {
    private val pools = hashMapOf<String, RecyclerView.RecycledViewPool>()

    override fun recyclerViewPool(key: String) = pools.getOrPut(key) {
        RecyclerView.RecycledViewPool().apply {
            // we expect the first viewType to be the most common,
            // so we increase the pool size
            setMaxRecycledViews(0, 16)
        }
    }

    override fun invalidateRecyclerViewPool() {
        pools.values.clear()
    }
}


fun RecyclerViewPoolProvider.configureRecyclerView(key: String, view: RecyclerView) {
    view.setRecycledViewPool(recyclerViewPool(key))

    // FIXME this would be nice, but, there seems to be a bug in androidx.fragment > 1.1.0.
    //  if we use a nested fragment, the view is detached from the window before
    //  the view state is saved. With the recycleChildrenOnDetach the recyclerview
    //  recycles and removes all views prior to saving their state.

    // if possible, recycle views on detach
    //    val lm = view.layoutManager
    //    if (lm is LinearLayoutManager) {
    //        lm.recycleChildrenOnDetach = true
    //    }
}
