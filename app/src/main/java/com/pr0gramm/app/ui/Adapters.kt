package com.pr0gramm.app.ui

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R.id.value
import com.pr0gramm.app.util.layoutInflater

interface AdapterDelegate<E : Any, VH : RecyclerView.ViewHolder> {
    fun isForViewType(values: List<E>, idx: Int): Boolean

    fun onCreateViewHolder(parent: ViewGroup): VH

    fun onBindViewHolder(holder: VH, values: List<E>, idx: Int)
}

abstract class ItemAdapterDelegate<E : T, T : Any, VH : RecyclerView.ViewHolder> : AdapterDelegate<T, VH> {
    final override fun isForViewType(values: List<T>, idx: Int): Boolean {
        return isForViewType(values[idx] as E)
    }

    abstract fun isForViewType(value: T): Boolean

    final override fun onBindViewHolder(holder: VH, values: List<T>, idx: Int) {
        return onBindViewHolder(holder, values[idx] as E)
    }

    abstract fun onBindViewHolder(holder: VH, value: E)
}

typealias SimpleItemAdapterDelegate<T, VH> = ItemAdapterDelegate<T, Any, VH>

class AdapterDelegateManager<T : Any>(
        private val delegates: List<AdapterDelegate<in T, RecyclerView.ViewHolder>>) {

    fun getItemViewType(values: List<T>, itemIndex: Int): Int {
        val idx = delegates.indexOfFirst { it.isForViewType(values, itemIndex) }
        if (idx == -1) {
            throw IllegalArgumentException("No adapter delegate for item $value")
        }

        return idx
    }

    fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val delegate = delegates[viewType]
        return delegate.onCreateViewHolder(parent)
    }

    fun onBindViewHolder(holder: RecyclerView.ViewHolder, values: List<T>, index: Int) {
        val delegate = delegates[holder.itemViewType]
        delegate.onBindViewHolder(holder, values, index)
    }
}

abstract class DelegatedAsyncListAdapter<T : Any>(
        diffCallback: DiffUtil.ItemCallback<T> = AsyncListAdapter.InstanceDiffCallback(),
        detectMoves: Boolean = false,
        name: String = "AsyncListAdapter") : AsyncListAdapter<T, RecyclerView.ViewHolder>(diffCallback, detectMoves, name) {

    protected val delegates: MutableList<AdapterDelegate<in T, out RecyclerView.ViewHolder>> = mutableListOf()

    private val manager by lazy(LazyThreadSafetyMode.NONE) {
        AdapterDelegateManager(delegates as List<AdapterDelegate<in T, RecyclerView.ViewHolder>>)
    }

    final override fun getItemViewType(position: Int): Int {
        return manager.getItemViewType(items, position)
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return manager.onCreateViewHolder(parent, viewType)
    }

    final override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        manager.onBindViewHolder(holder, items, position)
    }
}

fun <E : Any> staticLayoutAdapterDelegate(layout: Int, predicate: (E) -> Boolean)
        : AdapterDelegate<E, RecyclerView.ViewHolder> {

    class NoopViewHolder(view: View) : RecyclerView.ViewHolder(view)

    return object : ItemAdapterDelegate<E, E, RecyclerView.ViewHolder>() {
        override fun isForViewType(value: E): Boolean {
            return predicate(value)
        }

        override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            return NoopViewHolder(parent.layoutInflater.inflate(layout, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, value: E) {
        }
    }
}
