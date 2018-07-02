package com.pr0gramm.app.ui.base

import android.support.annotation.IdRes
import android.view.View
import gnu.trove.map.TIntObjectMap
import gnu.trove.map.hash.TIntObjectHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ViewCache(val lookupView: (Int) -> View?) {
    private val cache: TIntObjectMap<Any> = TIntObjectHashMap(64)

    fun <R, V : View> bindView(@IdRes id: Int): ReadOnlyProperty<R, V> {
        return object : ReadOnlyProperty<R, V> {
            override fun getValue(thisRef: R, property: KProperty<*>): V {
                val result = cache[id] ?: run {
                    val view = lookupView(id)
                            ?: throw IllegalArgumentException("Could not find view ${property.name} on $thisRef")

                    cache.put(id, view)
                    view
                }

                @Suppress("UNCHECKED_CAST")
                return result as V
            }
        }
    }

    fun <R, V> bindOptionalView(id: Int): ReadOnlyProperty<R, V?> {
        return object : ReadOnlyProperty<R, V?> {
            override fun getValue(thisRef: R, property: KProperty<*>): V? {
                val result = cache[id] ?: run {
                    val view = lookupView(id)?.also { cache.put(id, it) }
                    view
                }

                @Suppress("UNCHECKED_CAST")
                return result as V?
            }
        }
    }

    fun reset() {
        this.cache.clear()
    }
}

interface HasViewCache {
    val viewCache: ViewCache

    fun <T, V : View> bindView(id: Int) = viewCache.bindView<T, V>(id)
    fun <T, V : View> bindOptionalView(id: Int) = viewCache.bindOptionalView<T, V?>(id)
}
