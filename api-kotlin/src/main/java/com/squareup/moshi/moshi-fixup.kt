package com.squareup.moshi


internal fun removeClassJsonAdapter() {
    Moshi.BUILT_IN_FACTORIES.remove(ClassJsonAdapter.FACTORY)
}
