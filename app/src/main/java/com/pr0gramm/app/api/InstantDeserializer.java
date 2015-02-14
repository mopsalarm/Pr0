package com.pr0gramm.app.api;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import org.joda.time.Instant;

import java.lang.reflect.Type;

/**
 * Converts a timestamp from seconds since the epoch into a
 * instant during json deserialization.
 */
public class InstantDeserializer implements JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return new Instant(json.getAsLong() * 1000L);
    }
}
