package com.pr0gramm.app.ui.adapters2

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.util.inflateDetachedChild
import java.lang.reflect.ParameterizedType

abstract class BindableViewHolder<E : Any>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    constructor(parent: ViewGroup, @LayoutRes layoutId: Int) : this(parent.inflateDetachedChild(layoutId))

    abstract fun bindTo(value: E)

    interface Factory<E : Any> {
        fun convertValue(value: Any): E?
        fun createViewHolder(parent: ViewGroup): BindableViewHolder<E>
    }

    abstract class DefaultFactory<E : Any>(private val create: (ViewGroup) -> BindableViewHolder<E>) : Factory<E> {
        @Suppress("UNCHECKED_CAST")
        private val eType = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<E>

        override fun convertValue(value: Any): E? {
            return if (eType.isInstance(value)) eType.cast(value) else null
        }

        override fun createViewHolder(parent: ViewGroup): BindableViewHolder<E> {
            return create(parent)
        }
    }
}

inline fun <reified E : Any> createStaticViewHolder(
        @LayoutRes layoutId: Int,
        crossinline convertValue: (Any) -> E? = { value: Any -> value as? E }): BindableViewHolder.Factory<E> {

    return object : BindableViewHolder.Factory<E> {
        override fun createViewHolder(parent: ViewGroup): BindableViewHolder<E> {
            return object : BindableViewHolder<E>(parent, layoutId) {
                override fun bindTo(value: E) = Unit
            }
        }

        override fun convertValue(value: Any): E? = convertValue(value)
    }
}

class ViewHolders<T : Any>(builder: FactoryBuilderScope<T>.() -> Unit) {
    @Suppress("UNCHECKED_CAST")
    private val factories: List<BindableViewHolder.Factory<T>> = run {
        ArrayList<BindableViewHolder.Factory<T>>().also { list ->
            FactoryBuilderScope(list).apply(builder)
        }
    }

    fun getItemViewType(value: T): Int {
        val index = factories.indexOfFirst { f -> f.canHandle(value) }
        if (index == -1) {
            throw IllegalArgumentException("No view holder factory for value: '$value'")
        }

        return index
    }

    fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<T> {
        return factories[viewType].createViewHolder(parent)
    }

    fun onBindViewHolder(holder: BindableViewHolder<T>, value: T) {
        holder.bindTo(value)
    }

    private fun <T : Any> BindableViewHolder.Factory<T>.canHandle(value: T): Boolean {
        return convertValue(value) != null
    }

    class FactoryBuilderScope<T : Any>(private val factories: MutableList<BindableViewHolder.Factory<T>>) {
        fun <E : T> register(factory: BindableViewHolder.Factory<E>) {
            factories += factory as BindableViewHolder.Factory<T>
        }

        inline fun <reified E : T> staticViewHolder(
                @LayoutRes layoutId: Int,
                crossinline convertValue: (Any) -> E? = { value: Any -> value as? E }) {

            register(createStaticViewHolder(layoutId, convertValue))
        }
    }
}
