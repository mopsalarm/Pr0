package com.pr0gramm.app.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

interface RecyclerViewPoolProvider {
    fun recyclerViewPool(key: String): RecyclerView.RecycledViewPool
}

class RecyclerViewPoolMap : RecyclerViewPoolProvider {
    private val pools = mutableMapOf<String, RecyclerView.RecycledViewPool>()
    override fun recyclerViewPool(key: String) = pools.getOrPut(key) {
        RecyclerView.RecycledViewPool().apply {
            // we expect the first viewType to be the most common,
            // so we increase the pool size
            setMaxRecycledViews(0, 16)
        }
    }
}


fun RecyclerViewPoolProvider.configureRecyclerView(key: String, view: RecyclerView) {
    view.setRecycledViewPool(recyclerViewPool(key))

    // if possible, recycle views on detach
    val lm = view.layoutManager
    if (lm is LinearLayoutManager) {
        lm.recycleChildrenOnDetach = true
    }
}
