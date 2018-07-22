package com.squareup.moshi

fun removeClassJsonAdapter() {
    Moshi.BUILT_IN_FACTORIES.remove(ClassJsonAdapter.FACTORY)
}
