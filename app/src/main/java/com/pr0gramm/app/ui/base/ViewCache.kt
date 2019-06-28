package com.pr0gramm.app.ui.base

import android.view.View
import androidx.annotation.IdRes
import androidx.collection.SparseArrayCompat
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ViewCache(val lookupView: (Int) -> View?) {
    private val cache: SparseArrayCompat<Any> = SparseArrayCompat(64)

    fun <V : View> bindView(viewType: Class<V>, @IdRes id: Int): ReadOnlyProperty<Any, V> {
        return object : ReadOnlyProperty<Any, V> {
            override fun getValue(thisRef: Any, property: KProperty<*>): V {
                val result = cache[id]
                if (result != null) {
                    return viewType.cast(result) as V
                }

                val view = lookupView(id)
                        ?: throw IllegalArgumentException("Could not find view ${property.name} on $thisRef")

                cache.put(id, view)

                return viewType.cast(view) as V
            }
        }
    }

    fun <V : View> bindOptionalView(viewType: Class<V>, @IdRes id: Int): ReadOnlyProperty<Any, V?> {
        return object : ReadOnlyProperty<Any, V?> {
            override fun getValue(thisRef: Any, property: KProperty<*>): V? {
                val result = cache[id]
                if (result != null)
                    return viewType.cast(result)

                val view = lookupView(id)
                if (view != null) {
                    cache.put(id, view)
                }

                return viewType.cast(view)
            }
        }
    }

    fun reset() {
        this.cache.clear()
    }
}

interface HasViewCache {
    val viewCache: ViewCache
}

inline fun <reified V : View> HasViewCache.bindView(id: Int) = viewCache.bindView(V::class.java, id)

inline fun <reified V : View> HasViewCache.bindOptionalView(id: Int) = viewCache.bindOptionalView(V::class.java, id)