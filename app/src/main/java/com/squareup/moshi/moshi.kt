package com.squareup.moshi

fun removeClassJsonAdapter() {
    val removed = Moshi.BUILT_IN_FACTORIES.remove(ClassJsonAdapter.FACTORY)
    if (!removed) {
        throw IllegalStateException("No ClassJsonAdapter found, please investigate.")
    }
}
