package com.pr0gramm.app

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

object NoValue

internal object NothingAdapter : JsonAdapter<NoValue>() {
    override fun fromJson(reader: JsonReader): NoValue? {
        reader.skipValue()
        return NoValue
    }

    override fun toJson(writer: JsonWriter, value: NoValue?) {
        writer.nullValue()
    }
}