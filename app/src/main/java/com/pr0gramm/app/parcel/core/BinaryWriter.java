package com.pr0gramm.app.parcel.core;

import android.annotation.TargetApi;
import android.os.Build;

import com.google.common.base.Throwables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.SignedBytes;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

import gnu.trove.map.hash.TObjectByteHashMap;

/**
 */
class BinaryWriter extends JsonWriter {
    private final ByteArrayDataOutput output = ByteStreams.newDataOutput();
    private final TObjectByteHashMap<String> nameCache = new TObjectByteHashMap<>(32, 0.7f, (byte) -1);

    BinaryWriter() {
        super(CharStreams.nullWriter());
    }

    private void token(ProtocolToken token) {
        output.writeByte(token.ordinal());
    }

    @Override
    public JsonWriter beginArray() throws IOException {
        token(ProtocolToken.ARRAY_BEGIN);
        return this;
    }

    @Override
    public JsonWriter endArray() throws IOException {
        token(ProtocolToken.ARRAY_END);
        return this;
    }

    @Override
    public JsonWriter beginObject() throws IOException {
        token(ProtocolToken.OBJECT_BEGIN);
        return this;
    }

    @Override
    public JsonWriter endObject() throws IOException {
        token(ProtocolToken.OBJECT_END);
        return this;
    }

    @Override
    public JsonWriter name(String name) throws IOException {
        byte ref = nameCache.get(name);
        if (ref == nameCache.getNoEntryValue()) {
            token(ProtocolToken.NAME);
            output.writeUTF(name);

            // cache for next time if space is available
            if (nameCache.size() < Byte.MAX_VALUE) {
                nameCache.put(name, (byte) nameCache.size());
            }

        } else {
            token(ProtocolToken.NAME_REF);
            output.writeByte(ref);
        }

        return this;
    }

    @Override
    public JsonWriter value(String value) throws IOException {
        token(ProtocolToken.STRING);
        output.writeUTF(value);
        return this;
    }

    @Override
    public JsonWriter nullValue() throws IOException {
        token(ProtocolToken.NULL);
        return this;
    }

    @Override
    public JsonWriter value(boolean value) throws IOException {
        token(value ? ProtocolToken.BOOLEAN_TRUE : ProtocolToken.BOOLEAN_FALSE);
        return this;
    }

    @Override
    public JsonWriter value(double value) throws IOException {
        token(ProtocolToken.DOUBLE);
        output.writeDouble(value);
        return this;
    }

    @Override
    public JsonWriter value(long value) throws IOException {
        // Write the value with the least amount of bytes
        if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE) {
            token(ProtocolToken.BYTE);
            output.writeByte(SignedBytes.checkedCast(value));

        } else if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE) {
            token(ProtocolToken.SHORT);
            output.writeShort(Shorts.checkedCast(value));

        } else if (Integer.MIN_VALUE <= value && value <= Integer.MAX_VALUE) {
            token(ProtocolToken.INTEGER);
            output.writeInt(Ints.checkedCast(value));

        } else {
            token(ProtocolToken.LONG);
            output.writeLong(value);
        }

        return this;
    }

    @Override
    public JsonWriter value(Number value) throws IOException {
        Class<? extends Number> clazz = value.getClass();

        if (clazz == Long.class || clazz == Integer.class || clazz == Short.class || clazz == Byte.class) {
            value(value.longValue());

        } else if (clazz == Float.class) {
            token(ProtocolToken.FLOAT);
            output.writeFloat(value.floatValue());

        } else {
            value(value.doubleValue());
        }

        return this;
    }

    @Override
    public void close() {
        token(ProtocolToken.DOCUMENT_END);
    }

    /**
     * Convert the output to a byte array.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public byte[] toByteArray() {
        byte[] raw = output.toByteArray();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(raw.length + 1)) {
            if (raw.length > 1024) {
                outputStream.write(ContainerFormat.DEFLATE);
                try (OutputStream deflateStream = new DeflaterOutputStream(outputStream)) {
                    deflateStream.write(raw);
                }

            } else {
                outputStream.write(ContainerFormat.RAW);
                outputStream.write(raw);
            }

            return outputStream.toByteArray();
        } catch (IOException err) {
            throw Throwables.propagate(err);
        }
    }
}
