package com.pr0gramm.app.parcel.core;

import android.os.Parcel;

import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 */
class ParcelReader extends JsonReader {
    private final Parcel parcel;
    private JsonToken next;

    private final List<String> nameCache = new ArrayList<>();

    /**
     * Creates a new instance that reads a JSON-encoded stream from {@code in}.
     */
    public ParcelReader(Parcel in) {
        super(new StringReader(""));
        this.parcel = in;
    }

    private void consume(JsonToken token) throws IOException {
        if (peek() != token) {
            throw new IOException("Expected " + token + " but got " + next);
        }

        next = null;
    }

    @Override
    public void beginArray() throws IOException {
        consume(JsonToken.BEGIN_ARRAY);
    }

    @Override
    public void endArray() throws IOException {
        consume(JsonToken.END_ARRAY);
    }

    @Override
    public void beginObject() throws IOException {
        consume(JsonToken.BEGIN_OBJECT);
    }

    @Override
    public void endObject() throws IOException {
        consume(JsonToken.END_OBJECT);
    }

    @Override
    public boolean hasNext() throws IOException {
        JsonToken next = peek();
        return next != JsonToken.END_ARRAY
                && next != JsonToken.END_OBJECT
                && next != JsonToken.END_DOCUMENT;
    }

    @Override
    public JsonToken peek() throws IOException {
        if (next == null)
            next = TOKENS.get(parcel.readByte());

        return next;
    }

    @Override
    public String nextName() throws IOException {
        consume(JsonToken.NAME);
        switch (parcel.readByte()) {
            case ParcelContext.NAME_FOLLOWING:
                String name = parcel.readString();
                nameCache.add(name);
                return name;

            case ParcelContext.NAME_REFERENCE:
                return nameCache.get(parcel.readByte() & 0xff);

            default:
                throw new IOException("Invalid name command");
        }
    }

    @Override
    public String nextString() throws IOException {
        consume(JsonToken.STRING);
        return parcel.readString();
    }

    @Override
    public boolean nextBoolean() throws IOException {
        consume(JsonToken.BOOLEAN);
        return parcel.readByte() != 0;
    }

    @Override
    public void nextNull() throws IOException {
        consume(JsonToken.NULL);
    }

    private Number readNumber() throws IOException {
        consume(JsonToken.NUMBER);
        switch (parcel.readByte()) {
            case ParcelContext.NUMBER_LONG:
                return parcel.readLong();

            case ParcelContext.NUMBER_INTEGER:
                return parcel.readInt();

            case ParcelContext.NUMBER_BYTE:
                return parcel.readByte();

            case ParcelContext.NUMBER_DOUBLE:
                return parcel.readDouble();

            case ParcelContext.NUMBER_FLOAT:
                return parcel.readFloat();

            default:
                throw new IOException("Invalid number type");
        }
    }

    @Override
    public double nextDouble() throws IOException {
        return readNumber().doubleValue();
    }

    @Override
    public long nextLong() throws IOException {
        return readNumber().longValue();
    }

    @Override
    public int nextInt() throws IOException {
        return readNumber().intValue();
    }

    @Override
    public void close() throws IOException {
        consume(JsonToken.END_DOCUMENT);
        super.close();
    }

    @Override
    public void skipValue() throws IOException {
        switch (peek()) {
            case BEGIN_ARRAY:
                skipArray();
                break;
            case END_ARRAY:
                endArray();
                break;
            case BEGIN_OBJECT:
                skipObject();
                break;
            case END_OBJECT:
                endObject();
                break;
            case NAME:
                nextName();
                break;
            case STRING:
                nextString();
                break;
            case NUMBER:
                readNumber();
                break;
            case BOOLEAN:
                nextBoolean();
                break;
            case NULL:
                nextNull();
                break;
        }
    }

    private void skipObject() throws IOException {
        beginObject();
        while (hasNext()) {
            nextName();
            skipValue();
        }

        endObject();
    }

    private void skipArray() throws IOException {
        beginArray();
        while (hasNext())
            skipValue();

        endArray();
    }

    @Override
    public String getPath() {
        return "no path available at " + parcel.dataPosition();
    }

    private final static ImmutableList<JsonToken> TOKENS = ImmutableList.copyOf(JsonToken.values());
}
