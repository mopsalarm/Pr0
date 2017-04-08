package com.pr0gramm.app.parcel.core

import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams
import com.google.common.primitives.Ints
import com.google.common.primitives.Shorts
import com.google.common.primitives.SignedBytes
import com.google.gson.stream.JsonWriter
import gnu.trove.map.hash.TObjectByteHashMap
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

/**
 */
internal class BinaryWriter : JsonWriter(CharStreams.nullWriter()) {
    private val output = ByteStreams.newDataOutput()
    private val nameCache = TObjectByteHashMap<String>(32, 0.7f, (-1).toByte())

    private fun token(token: ProtocolToken) {
        output.writeByte(token.ordinal)
    }

    override fun beginArray(): JsonWriter {
        token(ProtocolToken.ARRAY_BEGIN)
        return this
    }

    override fun endArray(): JsonWriter {
        token(ProtocolToken.ARRAY_END)
        return this
    }

    override fun beginObject(): JsonWriter {
        token(ProtocolToken.OBJECT_BEGIN)
        return this
    }

    override fun endObject(): JsonWriter {
        token(ProtocolToken.OBJECT_END)
        return this
    }

    override fun name(name: String): JsonWriter {
        val ref = nameCache.get(name)
        if (ref == nameCache.noEntryValue) {
            token(ProtocolToken.NAME)
            output.writeUTF(name)

            // cache for next time if space is available
            if (nameCache.size() < java.lang.Byte.MAX_VALUE) {
                nameCache.put(name, nameCache.size().toByte())
            }

        } else {
            token(ProtocolToken.NAME_REF)
            output.writeByte(ref.toInt())
        }

        return this
    }

    override fun value(value: String?): JsonWriter {
        token(ProtocolToken.STRING)
        output.writeUTF(value!!)
        return this
    }

    override fun nullValue(): JsonWriter {
        token(ProtocolToken.NULL)
        return this
    }

    override fun value(value: Boolean): JsonWriter {
        token(if (value) ProtocolToken.BOOLEAN_TRUE else ProtocolToken.BOOLEAN_FALSE)
        return this
    }

    override fun value(value: Double): JsonWriter {
        token(ProtocolToken.DOUBLE)
        output.writeDouble(value)
        return this
    }

    override fun value(value: Long): JsonWriter {
        // Write the value with the least amount of bytes
        if (java.lang.Byte.MIN_VALUE <= value && value <= java.lang.Byte.MAX_VALUE) {
            token(ProtocolToken.BYTE)
            output.writeByte(SignedBytes.checkedCast(value).toInt())

        } else if (java.lang.Short.MIN_VALUE <= value && value <= java.lang.Short.MAX_VALUE) {
            token(ProtocolToken.SHORT)
            output.writeShort(Shorts.checkedCast(value).toInt())

        } else if (Integer.MIN_VALUE <= value && value <= Integer.MAX_VALUE) {
            token(ProtocolToken.INTEGER)
            output.writeInt(Ints.checkedCast(value))

        } else {
            token(ProtocolToken.LONG)
            output.writeLong(value)
        }

        return this
    }

    override fun value(value: Number): JsonWriter {
        val clazz = value.javaClass

        when (clazz) {
            Long::class.java, Int::class.java, Short::class.java, Byte::class.java ->
                value(value.toLong())

            Float::class.java -> {
                token(ProtocolToken.FLOAT)
                output.writeFloat(value.toFloat())
            }

            else -> value(value.toDouble())
        }

        return this
    }

    override fun close() {
        token(ProtocolToken.DOCUMENT_END)
    }

    /**
     * Convert the output to a byte array.
     */
    fun toByteArray(): ByteArray {
        val raw = output.toByteArray()

        ByteArrayOutputStream(raw.size + 1).use { outputStream ->
            if (raw.size > 1024) {
                outputStream.write(ContainerFormat.DEFLATE.toInt())
                DeflaterOutputStream(outputStream).use { it.write(raw) }

            } else {
                outputStream.write(ContainerFormat.RAW.toInt())
                outputStream.write(raw)
            }

            return outputStream.toByteArray()
        }
    }
}
