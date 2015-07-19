package com.pr0gramm.app.api;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.joda.time.Instant;

import java.io.IOException;

/**
 * Converts a timestamp from seconds since the epoch into a
 * instant during json deserialization.
 */
public class InstantTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        out.value(value.getMillis() / 1000);
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        return new Instant(in.nextLong() * 1000);
    }
}
