package com.pr0gramm.app.api.pr0gramm

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import org.joda.time.Instant
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

inline fun <reified T : Any> Moshi.Builder.adapter(adapter: JsonAdapter<T>): Moshi.Builder {
    return add(T::class.java, adapter)
}

inline fun <reified T : Any> Moshi.adapter(): JsonAdapter<T> {
    return adapter((object : TypeToken<T>() {}).type)
}

internal object InstantAdapter : JsonAdapter<Instant>() {
    override fun fromJson(reader: JsonReader): Instant {
        return Instant(reader.nextLong() * 1000L)
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
        val millis = value?.millis
        if (millis != null) {
            writer.value(millis / 1000)
        } else {
            writer.nullValue()
        }
    }
}

abstract class TypeToken<T> {
    val type: Type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
}