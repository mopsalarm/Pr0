package com.pr0gramm.app.parcel.core

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteStreams
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.util.zip.InflaterInputStream

/**
 */
internal class BinaryReader private constructor(private val input: ByteArrayDataInput) : JsonReader(StringReader("")) {

    private val nameCache = mutableListOf<String>()
    private var next: ProtocolToken? = null

    private fun consume(token: JsonToken): ProtocolToken {
        if (peek() != token) {
            throw IOException("Expected $token but got $next")
        }

        val current = this.next!!
        next = null
        return current
    }

    override fun peek(): JsonToken {
        if (next == null) {
            next = TOKENS[input.readByte().toInt()]
        }

        return next!!.token
    }

    override fun beginArray() {
        consume(JsonToken.BEGIN_ARRAY)
    }

    override fun endArray() {
        consume(JsonToken.END_ARRAY)
    }

    override fun beginObject() {
        consume(JsonToken.BEGIN_OBJECT)
    }

    override fun endObject() {
        consume(JsonToken.END_OBJECT)
    }

    override fun hasNext(): Boolean {
        val next = peek()
        return next != JsonToken.END_ARRAY
                && next != JsonToken.END_OBJECT
                && next != JsonToken.END_DOCUMENT
    }


    override fun nextName(): String {
        when (consume(JsonToken.NAME)) {
            ProtocolToken.NAME -> {
                val name = input.readUTF()
                nameCache.add(name)
                return name
            }

            ProtocolToken.NAME_REF -> return nameCache[input.readByte().toInt()]

            else -> throw IOException("Invalid name command")
        }
    }

    override fun nextString(): String {
        consume(JsonToken.STRING)
        return input.readUTF()
    }

    override fun nextBoolean(): Boolean {
        return consume(JsonToken.BOOLEAN) == ProtocolToken.BOOLEAN_TRUE
    }

    override fun nextNull() {
        consume(JsonToken.NULL)
    }

    private fun readNumber(): Number {
        when (consume(JsonToken.NUMBER)) {
            ProtocolToken.LONG -> return input.readLong()
            ProtocolToken.INTEGER -> return input.readInt()
            ProtocolToken.SHORT -> return input.readShort()
            ProtocolToken.BYTE -> return input.readByte()
            ProtocolToken.DOUBLE -> return input.readDouble()
            ProtocolToken.FLOAT -> return input.readFloat()
            else -> throw IOException("Invalid number type")
        }
    }

    override fun nextDouble(): Double {
        return readNumber().toDouble()
    }

    override fun nextLong(): Long {
        return readNumber().toLong()
    }

    override fun nextInt(): Int {
        return readNumber().toInt()
    }

    override fun close() {
        super.close()
        if (peek() != JsonToken.END_DOCUMENT)
            throw IOException("Expected DocumentEnd, got " + next!!)
    }

    override fun skipValue() {
        when (peek()) {
            JsonToken.BEGIN_ARRAY -> skipArray()
            JsonToken.END_ARRAY -> endArray()
            JsonToken.BEGIN_OBJECT -> skipObject()
            JsonToken.END_OBJECT -> endObject()
            JsonToken.NAME -> nextName()
            JsonToken.STRING -> nextString()
            JsonToken.NUMBER -> readNumber()
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.NULL -> nextNull()
            JsonToken.END_DOCUMENT -> Unit
        }
    }

    private fun skipObject() {
        beginObject()
        while (hasNext()) {
            nextName()
            skipValue()
        }

        endObject()
    }

    private fun skipArray() {
        beginArray()
        while (hasNext())
            skipValue()

        endArray()
    }

    override fun getPath(): String {
        return "no path available for binary reader"
    }

    companion object {
        private val TOKENS = ProtocolToken.values()

        fun from(input: ByteArray): BinaryReader {
            when (input[0]) {
                ContainerFormat.RAW -> return BinaryReader(ByteStreams.newDataInput(input, 1))

                ContainerFormat.DEFLATE -> {
                    val uncompressed = inflate(input, 1)
                    return BinaryReader(ByteStreams.newDataInput(uncompressed))
                }

                else -> throw IllegalArgumentException("Invalid container format for binary json")
            }
        }

        /**
         * Uncompresses gzip compresse data.
         */
        private fun inflate(input: ByteArray, start: Int): ByteArray {
            ByteArrayInputStream(input, start, input.size - start).use {
                inputStream ->
                InflaterInputStream(inputStream).use {
                    inflated ->
                    return ByteStreams.toByteArray(inflated)
                }
            }
        }
    }
}
