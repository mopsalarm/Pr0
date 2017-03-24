package com.pr0gramm.app.parcel.core;

import android.annotation.TargetApi;
import android.os.Build;

import com.google.common.base.Throwables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

/**
 */
class BinaryReader extends JsonReader {
    private final static ProtocolToken[] TOKENS = ProtocolToken.values();

    private final List<String> nameCache = new ArrayList<>();
    private final ByteArrayDataInput input;
    private ProtocolToken next;

    /**
     * Creates a new instance that reads a JSON-encoded stream from {@code in}.
     */
    private BinaryReader(ByteArrayDataInput input) {
        super(new StringReader(""));
        this.input = input;
    }

    private ProtocolToken consume(JsonToken token) throws IOException {
        if (peek() != token) {
            throw new IOException("Expected " + token + " but got " + next);
        }

        ProtocolToken current = this.next;
        next = null;
        return current;
    }

    @Override
    public JsonToken peek() throws IOException {
        if (next == null) {
            next = TOKENS[input.readByte()];
        }

        return next.token;
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
    public String nextName() throws IOException {
        switch (consume(JsonToken.NAME)) {
            case NAME:
                String name = input.readUTF();
                nameCache.add(name);
                return name;

            case NAME_REF:
                return nameCache.get(input.readByte());

            default:
                throw new IOException("Invalid name command");
        }
    }

    @Override
    public String nextString() throws IOException {
        consume(JsonToken.STRING);
        return input.readUTF();
    }

    @Override
    public boolean nextBoolean() throws IOException {
        return consume(JsonToken.BOOLEAN) == ProtocolToken.BOOLEAN_TRUE;
    }

    @Override
    public void nextNull() throws IOException {
        consume(JsonToken.NULL);
    }

    private Number readNumber() throws IOException {
        switch (consume(JsonToken.NUMBER)) {
            case LONG:
                return input.readLong();

            case INTEGER:
                return input.readInt();

            case SHORT:
                return input.readShort();

            case BYTE:
                return input.readByte();

            case DOUBLE:
                return input.readDouble();

            case FLOAT:
                return input.readFloat();

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
        super.close();
        if (peek() != JsonToken.END_DOCUMENT)
            throw new IOException("Expected DocumentEnd, got " + next);
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
        return "no path available for binary reader";
    }

    public static BinaryReader from(byte[] input) {
        switch (input[0]) {
            case ContainerFormat.RAW:
                return new BinaryReader(ByteStreams.newDataInput(input, 1));

            case ContainerFormat.DEFLATE:
                byte[] uncompressed = inflate(input, 1);
                return new BinaryReader(ByteStreams.newDataInput(uncompressed));

            default:
                throw new IllegalArgumentException("Invalid container format for binary json");
        }
    }

    /**
     * Uncompresses gzip compresse data.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static byte[] inflate(byte[] input, int start) {
        try {
            try (InputStream inputStream = new ByteArrayInputStream(input, start, input.length - start)) {
                try (InputStream inflated = new InflaterInputStream(inputStream)) {
                    return ByteStreams.toByteArray(inflated);
                }
            }

        } catch (IOException err) {
            throw Throwables.propagate(err);
        }
    }
}
