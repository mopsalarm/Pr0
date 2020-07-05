package com.pr0gramm.app.ui.adapters2

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil


abstract class DelegatingPagingDataAdapter<T : Any>(
        diffCallback: DiffUtil.ItemCallback<T>) : PagingDataAdapter<T, BindableViewHolder<T>>(diffCallback) {

    protected abstract val delegate: ViewHolders<T>

    val items = object : AbstractList<T>() {
        override val size: Int get() = itemCount
        override fun get(index: Int): T = getItem(index)
                ?: throw UnsupportedOperationException("placeholder not supported")
    }

    final override fun getItemViewType(position: Int): Int {
        return delegate.getItemViewType(mustGetItem(position))
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<T> {
        return delegate.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: BindableViewHolder<T>, position: Int) {
        return delegate.onBindViewHolder(holder, mustGetItem(position))
    }

    private fun mustGetItem(position: Int): T {
        return getItem(position) ?: throw RuntimeException("placeholder values not supported")
    }
}
