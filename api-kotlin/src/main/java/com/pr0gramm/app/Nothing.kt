package com.pr0gramm.app

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

object Nothing

object NothingAdapter : JsonAdapter<Nothing>() {
    override fun fromJson(reader: JsonReader?): Nothing? {
        return Nothing
    }

    override fun toJson(writer: JsonWriter?, value: Nothing?) {
    }
}