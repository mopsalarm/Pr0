package com.pr0gramm.app.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.joda.time.Instant

/**
 * Converts a timestamp from seconds since the epoch into a
 * instant during json deserialization.
 */
class InstantTypeAdapter : TypeAdapter<Instant>() {
    override fun write(out: JsonWriter, value: Instant) {
        out.value(value.millis / 1000)
    }

    override fun read(input: JsonReader): Instant {
        return Instant(input.nextLong() * 1000)
    }
}
