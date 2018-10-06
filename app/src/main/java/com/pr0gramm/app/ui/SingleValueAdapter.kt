package com.pr0gramm.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 */
abstract class SingleValueAdapter<T, V : View>(private val value: T) :
        androidx.recyclerview.widget.RecyclerView.Adapter<SingleValueAdapter.Holder<V>>() {

    init {
        setHasStableIds(true)
    }

    final override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(hasStableIds)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<V> {
        return Holder(createView(parent.context, parent))
    }

    override fun onBindViewHolder(holder: Holder<V>, position: Int) {
        bindView(holder.view, value)
    }

    override fun getItemCount(): Int {
        return 1
    }

    protected abstract fun createView(context: Context, parent: ViewGroup): V

    protected abstract fun bindView(view: V, value: T)


    class Holder<out V : View>(internal val view: V) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
}
