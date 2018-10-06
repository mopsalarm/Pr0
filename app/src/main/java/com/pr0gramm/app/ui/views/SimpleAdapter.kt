package com.pr0gramm.app.ui.views

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.pr0gramm.app.util.layoutInflater

private class Mapping<T>(val predicate: TypePredicate<T>, val newView: NewViewHolder<T>)

typealias NewViewHolder<T> = (parent: ViewGroup) -> SimpleAdapter.ViewHolder<T>

typealias TypePredicate<T> = (value: T) -> Boolean

@DslMarker
annotation class AdapterMarker

class SimpleAdapter<T> private constructor(private val mappings: List<Mapping<T>>, diffCallback: DiffUtil.ItemCallback<T>) : ListAdapter<T, SimpleAdapter.ViewHolder<T>>(diffCallback) {

    operator fun get(idx: Int): T = getItem(idx)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        return mappings[viewType].newView(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        holder.bind(this[position])
    }

    override fun getItemViewType(position: Int): Int {
        val item = this[position]
        val viewType = mappings.indexOfFirst { it.predicate(item) }
        if (viewType == -1) {
            throw RuntimeException("illegal type, no view registered")
        }

        return viewType
    }

    class ViewHolder<T>(v: View, private val bind: ViewHolder<T>.(T) -> Unit) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
        fun bind(value: T) = this.bind.invoke(this, value)
    }

    @AdapterMarker
    class Builder<E> {
        private val mapping = mutableListOf<Mapping<E>>()

        private var sameCallback: (E, E) -> Boolean = { first, second -> first == second }
        private var equalCallback: (E, E) -> Boolean = { first, second -> first == second }

        private var fallback: Mapping<E> = Mapping({ true }) {
            throw NotImplementedError()
        }

        fun areItemsTheSame(cb: (E, E) -> Boolean) {
            sameCallback = cb
        }

        fun areContentsTheSame(cb: (E, E) -> Boolean) {
            equalCallback = cb
        }

        fun <T : E> test(p: TypePredicate<E>): PredicateBinder<T> {
            return PredicateBinder(p)
        }

        fun <T : E> fallback(): PredicateBinder<T> {
            return PredicateBinder { true }
        }

        inline fun <reified T : E> handle(): PredicateBinder<T> {
            return handle(T::class.java)
        }

        fun <T : E> handle(type: Class<T>): PredicateBinder<T> {
            return PredicateBinder { type.isInstance(it) }
        }

        inner class PredicateBinder<T : E> internal constructor(private val p: TypePredicate<E>) {
            infix fun with(vh: NewViewHolder<T>) {
                mapping += Mapping(p, vh as NewViewHolder<E>)
            }
        }

        fun <T : E> layout(l: Int, config: ViewHolderBinder<T>.(View) -> Unit): NewViewHolder<T> {
            return simpleView({ parent -> parent.layoutInflater.inflate(l, parent, false) }, config)
        }


        fun <T : E, V : View> simpleView(factory: (ViewGroup) -> V, config: ViewHolderBinder<T>.(view: View) -> Unit): NewViewHolder<T> {
            return { parent ->
                @Suppress("UNCHECKED_CAST")
                val view = factory(parent)
                ViewHolderBinder<T>(view).apply { config(view) }.build()
            }
        }

        class ViewHolderBinder<T>(private val view: View) {
            private var bind: androidx.recyclerview.widget.RecyclerView.ViewHolder.(value: T) -> Unit = {}

            fun bind(bind: androidx.recyclerview.widget.RecyclerView.ViewHolder.(value: T) -> Unit) {
                this.bind = bind
            }

            internal fun build() = SimpleAdapter.ViewHolder(view, bind)
        }

        internal fun build(): SimpleAdapter<E> = SimpleAdapter(mapping + fallback, object : DiffUtil.ItemCallback<E>() {
            override fun areItemsTheSame(oldItem: E, newItem: E): Boolean = sameCallback(oldItem, newItem)
            override fun areContentsTheSame(oldItem: E, newItem: E): Boolean = equalCallback(oldItem, newItem)
        })
    }
}

fun <T> recyclerViewAdapter(values: List<T> = listOf(), c: SimpleAdapter.Builder<T>.() -> Unit): SimpleAdapter<T> {
    return SimpleAdapter.Builder<T>().apply(c).build().apply { submitList(values) }
}
