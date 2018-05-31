package com.squareup.moshi

import java.lang.reflect.Modifier


internal fun Moshi.removeClassJsonAdapter(): Moshi {
    val adapters = fieldOf<MutableList<*>>(fieldOf<List<*>>(this))

    adapters?.removeAll { adapter ->
        adapter is ClassJsonAdapter<*>
    }

    return this
}

private inline fun <reified T> fieldOf(obj: Any?): T? {
    obj ?: return null

    val fieldValues = obj.javaClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }
            .map {
                it.isAccessible = true
                it.get(obj)
            }

    return fieldValues.filterIsInstance<T>().firstOrNull()
}