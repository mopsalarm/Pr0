package com.squareup.moshi

abstract class JsonWriterOpener : JsonWriter()

abstract class JsonReaderOpener : JsonReader()

val JsonReader.Options.tokens: Array<String> get() = this.strings

fun removeClassJsonAdapter() {
    Moshi.BUILT_IN_FACTORIES.remove(ClassJsonAdapter.FACTORY)
}
