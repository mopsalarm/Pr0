package com.squareup.moshi

abstract class JsonWriterOpener : JsonWriter()

abstract class JsonReaderOpener : JsonReader()


val JsonReader.Options.tokens: Array<String> get() = this.strings