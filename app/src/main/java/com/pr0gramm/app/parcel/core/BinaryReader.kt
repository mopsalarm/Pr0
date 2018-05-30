package com.pr0gramm.app.parcel.core

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteStreams
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReaderOpener
import com.squareup.moshi.tokens
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.InflaterInputStream

/**
 */
internal class BinaryReader private constructor(private val input: ByteArrayDataInput) : JsonReaderOpener() {
    private val nameCache = mutableListOf<String>()
    private var next: ProtocolToken? = null

    private fun consume(token: JsonReader.Token): ProtocolToken {
        if (peek() != token) {
            throw IOException("Expected $token but got $next")
        }

        val current = this.next!!
        next = null
        return current
    }

    override fun peek(): Token {
        if (next == null) {
            next = TOKENS[input.readByte().toInt()]
        }

        return next!!.token
    }

    override fun beginArray() {
        consume(JsonReader.Token.BEGIN_ARRAY)
    }

    override fun endArray() {
        consume(JsonReader.Token.END_ARRAY)
    }

    override fun beginObject() {
        consume(JsonReader.Token.BEGIN_OBJECT)
    }

    override fun endObject() {
        consume(JsonReader.Token.END_OBJECT)
    }

    override fun hasNext(): Boolean {
        val next = peek()
        return next != JsonReader.Token.END_ARRAY
                && next != JsonReader.Token.END_OBJECT
                && next != JsonReader.Token.END_DOCUMENT
    }

    override fun skipName() {
    }

    override fun selectName(options: Options): Int {
        return options.tokens.indexOf(nextName())
    }

    override fun selectString(options: Options): Int {
        return -1
    }


    override fun nextName(): String {
        when (consume(JsonReader.Token.NAME)) {
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
        consume(JsonReader.Token.STRING)
        return input.readUTF()
    }

    override fun nextBoolean(): Boolean {
        return consume(JsonReader.Token.BOOLEAN) == ProtocolToken.BOOLEAN_TRUE
    }

    override fun <T : Any?> nextNull(): T? {
        consume(JsonReader.Token.NULL)
        return null
    }

    private fun readNumber(): Number {
        when (consume(JsonReader.Token.NUMBER)) {
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
        if (peek() != JsonReader.Token.END_DOCUMENT)
            throw IOException("Expected DocumentEnd, got " + next!!)
    }

    override fun skipValue() {
        when (peek()) {
            JsonReader.Token.BEGIN_ARRAY -> skipArray()
            JsonReader.Token.END_ARRAY -> endArray()
            JsonReader.Token.BEGIN_OBJECT -> skipObject()
            JsonReader.Token.END_OBJECT -> endObject()
            JsonReader.Token.NAME -> nextName()
            JsonReader.Token.STRING -> nextString()
            JsonReader.Token.NUMBER -> readNumber()
            JsonReader.Token.BOOLEAN -> nextBoolean()
            JsonReader.Token.NULL -> nextNull<Void>()
            JsonReader.Token.END_DOCUMENT -> Unit
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
