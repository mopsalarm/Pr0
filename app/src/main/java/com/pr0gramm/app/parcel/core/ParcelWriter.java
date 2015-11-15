package com.pr0gramm.app.parcel.core;

import android.os.Parcel;

import com.google.common.io.CharStreams;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 */
class ParcelWriter extends JsonWriter {
    private final Parcel parcel;
    private final Map<String, Byte> nameCache = new HashMap<>();

    public ParcelWriter(Parcel dest) {
        super(CharStreams.nullWriter());

        this.parcel = dest;
    }

    private void token(JsonToken token) {
        parcel.writeByte((byte) token.ordinal());
    }

    @Override
    public JsonWriter beginArray() throws IOException {
        token(JsonToken.BEGIN_ARRAY);
        return this;
    }

    @Override
    public JsonWriter endArray() throws IOException {
        token(JsonToken.END_ARRAY);
        return this;
    }

    @Override
    public JsonWriter beginObject() throws IOException {
        token(JsonToken.BEGIN_OBJECT);
        return this;
    }

    @Override
    public JsonWriter endObject() throws IOException {
        token(JsonToken.END_OBJECT);
        return this;
    }

    @Override
    public JsonWriter name(String name) throws IOException {
        token(JsonToken.NAME);

        Byte ref = nameCache.get(name);
        if (ref == null) {
            parcel.writeByte(ParcelConstants.NAME_FOLLOWING);
            parcel.writeString(name);

            // cache for next time
            nameCache.put(name, (byte) nameCache.size());
        } else {
            parcel.writeByte(ParcelConstants.NAME_REFERENCE);
            parcel.writeByte(ref);
        }

        return this;
    }

    @Override
    public JsonWriter value(String value) throws IOException {
        token(JsonToken.STRING);
        parcel.writeString(value);
        return this;
    }

    @Override
    public JsonWriter nullValue() throws IOException {
        token(JsonToken.NULL);
        return this;
    }

    @Override
    public JsonWriter value(boolean value) throws IOException {
        token(JsonToken.BOOLEAN);
        parcel.writeByte((byte) (value ? 1 : 0));
        return this;
    }

    @Override
    public JsonWriter value(double value) throws IOException {
        token(JsonToken.NUMBER);
        parcel.writeByte(ParcelConstants.NUMBER_DOUBLE);
        parcel.writeDouble(value);
        return this;
    }

    @Override
    public JsonWriter value(long value) throws IOException {
        token(JsonToken.NUMBER);
        parcel.writeByte(ParcelConstants.NUMBER_LONG);
        parcel.writeLong(value);
        return this;
    }

    @Override
    public JsonWriter value(Number value) throws IOException {
        Class<? extends Number> clazz = value.getClass();

        if (clazz == Integer.class || clazz == Short.class) {
            token(JsonToken.NUMBER);
            parcel.writeByte(ParcelConstants.NUMBER_INTEGER);
            parcel.writeInt(value.intValue());
            return this;

        } else if (clazz == Byte.class) {
            token(JsonToken.NUMBER);
            parcel.writeByte(ParcelConstants.NUMBER_BYTE);
            parcel.writeByte(value.byteValue());
            return this;

        } else if (clazz == Float.class) {
            token(JsonToken.NUMBER);
            parcel.writeByte(ParcelConstants.NUMBER_FLOAT);
            parcel.writeFloat(value.floatValue());
            return this;

        } else if (clazz == Long.class) {
            return value(value.longValue());

        } else {
            return value(value.doubleValue());
        }
    }

    @Override
    public void close() throws IOException {
        token(JsonToken.END_DOCUMENT);
    }
}
