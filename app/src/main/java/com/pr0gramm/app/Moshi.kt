package com.pr0gramm.app

import com.squareup.moshi.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

val MoshiInstance: Moshi = run {
    removeClassJsonAdapter()

    Moshi.Builder()
            .adapter(InstantAdapter)
            .adapter(NothingAdapter)
            .adapter(Base64ByteArrayAdapter)
            .build()
}

abstract class TypeToken<T> {
    val type: Type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
}

inline fun <reified T : Any> Moshi.Builder.adapter(adapter: JsonAdapter<T>): Moshi.Builder {
    return add(T::class.java, adapter)
}

inline fun <reified T : Any> Moshi.adapter(): JsonAdapter<T> {
    return adapter((object : TypeToken<T>() {}).type)
}

private object InstantAdapter : JsonAdapter<Instant>() {
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

private object Base64ByteArrayAdapter : JsonAdapter<ByteArray>() {
    override fun fromJson(reader: JsonReader): ByteArray? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return null
        } else {
            return reader.nextString().decodeBase64()
        }
    }

    override fun toJson(writer: JsonWriter, value: ByteArray?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.encodeBase64())
        }
    }
}
