package com.pr0gramm.app.ui.adapters2

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.ui.AdapterDelegateManager


abstract class DelegatingPagingDataAdapter<T : Any>(
        diffCallback: DiffUtil.ItemCallback<T>) : PagingDataAdapter<T, RecyclerView.ViewHolder>(diffCallback) {

    protected abstract val manager: AdapterDelegateManager<T>

    val items = object : AbstractList<T>() {
        override val size: Int get() = itemCount
        override fun get(index: Int): T = getItem(index)
                ?: throw UnsupportedOperationException("placeholder not supported")
    }

    final override fun getItemViewType(position: Int): Int {
        return manager.getItemViewType(items, position)
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return manager.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        return manager.onBindViewHolder(holder, items, position)
    }
}
