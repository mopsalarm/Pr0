package com.pr0gramm.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.R.id.value
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.layoutInflater
import java.lang.reflect.ParameterizedType

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

abstract class ListItemValueAdapterDelegate<E : Any, VH : RecyclerView.ViewHolder>(private val itemValue: Any)
    : ItemAdapterDelegate<E, Any, VH>() {

    final override fun isForViewType(value: Any): Boolean {
        @Suppress("SuspiciousEqualsCombination")
        return itemValue === value || itemValue == value
    }
}

abstract class ListItemTypeAdapterDelegate<E : Any, VH : RecyclerView.ViewHolder>
    : ItemAdapterDelegate<E, Any, VH>() {

    // Get the actual type for E.
    @Suppress("UNCHECKED_CAST")
    private val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<E>

    final override fun isForViewType(value: Any): Boolean {
        return type.isInstance(value)
    }
}

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

abstract class DelegateAdapter<T : Any>(
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        manager.onBindViewHolder(holder, items, position)
    }
}

class NoopViewHolder(view: View) : RecyclerView.ViewHolder(view)

inline fun <E : Any> staticLayoutAdapterDelegate(layout: Int, crossinline predicate: (E) -> Boolean)
        : AdapterDelegate<E, RecyclerView.ViewHolder> {

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

inline fun <reified E : Any> staticLayoutAdapterDelegate(layout: Int)
        : AdapterDelegate<Any, RecyclerView.ViewHolder> {

    return staticLayoutAdapterDelegate(layout) { it is E }
}

fun staticLayoutAdapterDelegate(layout: Int, itemValue: Any)
        : AdapterDelegate<Any, RecyclerView.ViewHolder> {

    return staticLayoutAdapterDelegate(layout) {
        @Suppress("SuspiciousEqualsCombination")
        it === itemValue || it == itemValue
    }
}


class ErrorAdapterDelegate(private val layout: Int = R.layout.feed_error)
    : ListItemTypeAdapterDelegate<ErrorAdapterDelegate.Value, ErrorAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.layoutInflater.inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: Value) {
        holder.errorView.text = value.errorText
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val errorView = find<TextView>(R.id.error)
    }

    interface Value {
        val errorText: String
    }

    companion object {
        fun errorValueOf(context: Context, err: Exception): Value {
            return object : Value {
                override val errorText: String = ErrorFormatting.format(context, err)
            }
        }
    }
}
