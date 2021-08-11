package com.pr0gramm.app.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.pr0gramm.app.R
import com.pr0gramm.app.R.id.value
import com.pr0gramm.app.ui.views.BindingsViewHolder
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.trace
import kotlin.reflect.KClass

interface AdapterDelegate<E : Any, VH : RecyclerView.ViewHolder> {
    fun isForViewType(values: List<E>, idx: Int): Boolean

    fun onCreateViewHolder(parent: ViewGroup): VH

    fun onBindViewHolder(holder: VH, values: List<E>, idx: Int)

    /**
     * Save the adapters state (if any)
     */
    fun onSaveInstanceState(outState: Bundle) {}

    /**
     * Restore any previously saved instance state
     */
    fun onRestoreInstanceState(inState: Bundle) {}
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

abstract class ListItemTypeAdapterDelegate<E : L, L : Any, VH : RecyclerView.ViewHolder>(private val type: Class<E>)
    : ItemAdapterDelegate<E, L, VH>() {

    constructor(type: KClass<E>) : this(type.javaObjectType)

    final override fun isForViewType(value: L): Boolean {
        return type.isInstance(value)
    }
}

class AdapterDelegateManager<T : Any>(
        private val delegates: List<AdapterDelegate<T, RecyclerView.ViewHolder>>) {

    fun getItemViewType(values: List<T>, itemIndex: Int): Int {
        val idx = delegates.indexOfFirst { it.isForViewType(values, itemIndex) }
        if (idx == -1) {
            throw IllegalArgumentException("No adapter delegate for item $value")
        }

        return idx
    }

    fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val delegate = delegates[viewType]

        val holder = delegate.trace("onCreateViewHolder()") {
            delegate.onCreateViewHolder(parent)
        }

        holder.itemView.setTag(R.id.rv_view_type, viewType)

        return holder
    }

    fun onBindViewHolder(holder: RecyclerView.ViewHolder, values: List<T>, index: Int) {
        val delegate = delegates[holder.itemView.getTag(R.id.rv_view_type) as Int]
        delegate.onBindViewHolder(holder, values, index)
    }

    fun onSaveInstanceState(outState: Bundle) {
        delegates.forEach { it.onSaveInstanceState(outState) }
    }

    fun onRestoreInstanceState(inState: Bundle) {
        delegates.forEach { it.onRestoreInstanceState(inState) }
    }
}

abstract class DelegateAdapter<T : Any>(
        diffCallback: DiffUtil.ItemCallback<T> = AsyncListAdapter.InstanceDiffCallback(),
        detectMoves: Boolean = false)
    : AsyncListAdapter<T, RecyclerView.ViewHolder>(diffCallback, detectMoves),
        StatefulRecyclerView.InstanceStateAware {

    protected val delegates: MutableList<AdapterDelegate<in T, out RecyclerView.ViewHolder>> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    private val manager by lazy(LazyThreadSafetyMode.NONE) {
        AdapterDelegateManager(delegates as List<AdapterDelegate<T, RecyclerView.ViewHolder>>)
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is RecycleAware) {
            holder.onViewRecycled()
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val outState = Bundle()
        manager.onSaveInstanceState(outState)
        return outState
    }

    override fun onRestoreInstanceState(inState: Parcelable) {
        manager.onRestoreInstanceState(inState as Bundle)
    }
}

fun <T : Any> delegateAdapterOf(
        vararg delegates: AdapterDelegate<T, *>,
        diffCallback: DiffUtil.ItemCallback<T> = AsyncListAdapter.InstanceDiffCallback(),
        detectMoves: Boolean = false): DelegateAdapter<T> {

    return object : DelegateAdapter<T>(diffCallback, detectMoves) {
        init {
            this.delegates += delegates
        }
    }
}

interface RecycleAware {
    fun onViewRecycled()
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
    : ListItemTypeAdapterDelegate<ErrorAdapterDelegate.Value, Any, ErrorAdapterDelegate.ViewHolder>(Value::class) {

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

        fun errorValueOf(text: String): Value {
            return object : Value {
                override val errorText: String = text
            }
        }
    }
}


inline fun <reified AdaptedE : AdaptedT, reified AdaptedT : Any, E : T, T : Any, VH : RecyclerView.ViewHolder>
        ItemAdapterDelegate<E, T, VH>.adaptTo(crossinline convert: (AdaptedE) -> E): AdapterDelegate<AdaptedT, VH> {

    val delegate = this

    return object : ItemAdapterDelegate<AdaptedE, AdaptedT, VH>() {
        override fun onCreateViewHolder(parent: ViewGroup): VH {
            return delegate.onCreateViewHolder(parent)
        }

        override fun isForViewType(value: AdaptedT): Boolean {
            return value is AdaptedE && delegate.isForViewType(convert(value))
        }

        override fun onBindViewHolder(holder: VH, value: AdaptedE) {
            return delegate.onBindViewHolder(holder, convert(value))
        }
    }
}

interface SingleItemAdapterDelegate<T : Any, VH : RecyclerView.ViewHolder> {
    fun onCreateViewHolder(parent: ViewGroup): VH
    fun onBindViewHolder(holder: VH, item: T)
}

object Adapters {
    class ForViewBindings<T : Any, B : ViewBinding>(
            private val inflate: (LayoutInflater, parent: ViewGroup?, attachToParent: Boolean) -> B,
            private val bindView: (holder: BindingsViewHolder<B>, value: T) -> Unit,
    ) : SingleItemAdapterDelegate<T, BindingsViewHolder<B>> {
        override fun onCreateViewHolder(parent: ViewGroup): BindingsViewHolder<B> {
            return BindingsViewHolder(inflate(parent.layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: BindingsViewHolder<B>, item: T) {
            bindView(holder, item)
        }
    }

    fun <E : Any, VH : RecyclerView.ViewHolder, A : SingleItemAdapterDelegate<E, VH>> forAll(adapter: A): AdapterDelegate<E, RecyclerView.ViewHolder> {
        return adapt(adapter) { value -> value }
    }

    fun <E : Any, T : Any, VH : RecyclerView.ViewHolder, A : SingleItemAdapterDelegate<E, VH>> adapt(adapter: A, convert: (T) -> E?): AdapterDelegate<T, RecyclerView.ViewHolder> {
        return object : AdapterDelegate<T, RecyclerView.ViewHolder> {
            override fun isForViewType(values: List<T>, idx: Int): Boolean {
                return convert(values[idx]) != null
            }

            override fun onCreateViewHolder(parent: ViewGroup): VH {
                return adapter.onCreateViewHolder(parent)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, values: List<T>, idx: Int) {
                // onBindViewHolder is only called after isForViewType, so convert
                // must always return a non-null value here.
                val item = convert(values[idx])!!

                @Suppress("UNCHECKED_CAST")
                adapter.onBindViewHolder(holder as VH, item)
            }
        }
    }
}
