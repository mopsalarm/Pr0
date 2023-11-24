package com.pr0gramm.app

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.removeClassJsonAdapter

val MoshiInstance: Moshi = run {
    removeClassJsonAdapter()

    Moshi.Builder()
            .adapter(InstantAdapter.nullSafe())
            .adapter(Base64ByteArrayAdapter)
            .adapter(BooleanAdapter)
            .build()
}

inline fun <reified T : Any> Moshi.Builder.adapter(adapter: JsonAdapter<T>): Moshi.Builder {
    T::class.javaPrimitiveType?.let { add(it, adapter) }
    return add(T::class.java, adapter)
}

private object InstantAdapter : JsonAdapter<Instant>() {
    override fun fromJson(reader: JsonReader): Instant {
        return Instant.ofEpochSeconds(reader.nextLong())
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
        if (value != null) {
            writer.value(value.epochSeconds)
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

private object BooleanAdapter : JsonAdapter<Boolean>() {
    override fun fromJson(reader: JsonReader): Boolean {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> false
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NUMBER -> reader.nextLong() != 0L
            JsonReader.Token.STRING -> isTruthValue(reader.nextString())
            else -> throw JsonDataException("tried to read boolean, but got ${reader.peek()}")
        }
    }

    private fun isTruthValue(value: String?): Boolean {
        return "true".equals(value, ignoreCase = true) || value == "1"
    }

    override fun toJson(writer: JsonWriter, value: Boolean?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}
