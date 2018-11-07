package com.pr0gramm.app

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.EOFException

object NoValue

internal object NothingAdapter : JsonAdapter<NoValue>() {
    override fun fromJson(reader: JsonReader): NoValue? {
        try {
            if (reader.hasNext()) {
                reader.skipValue()
            }
        } catch (err: EOFException) {
            // ignored, we accept even empty documents.
        }

        return NoValue
    }

    override fun toJson(writer: JsonWriter, value: NoValue?) {
        writer.nullValue()
    }
}