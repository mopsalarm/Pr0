package com.pr0gramm.app.parcel.core

import com.squareup.moshi.JsonReader

/**
 */
internal enum class ProtocolToken constructor(val token: JsonReader.Token) {
    NULL(JsonReader.Token.NULL),
    STRING(JsonReader.Token.STRING),

    BYTE(JsonReader.Token.NUMBER),
    SHORT(JsonReader.Token.NUMBER),
    INTEGER(JsonReader.Token.NUMBER),
    LONG(JsonReader.Token.NUMBER),
    FLOAT(JsonReader.Token.NUMBER),
    DOUBLE(JsonReader.Token.NUMBER),

    ARRAY_BEGIN(JsonReader.Token.BEGIN_ARRAY),
    ARRAY_END(JsonReader.Token.END_ARRAY),

    OBJECT_BEGIN(JsonReader.Token.BEGIN_OBJECT),
    OBJECT_END(JsonReader.Token.END_OBJECT),
    NAME(JsonReader.Token.NAME),
    NAME_REF(JsonReader.Token.NAME),

    BOOLEAN_TRUE(JsonReader.Token.BOOLEAN),
    BOOLEAN_FALSE(JsonReader.Token.BOOLEAN),

    DOCUMENT_END(JsonReader.Token.END_DOCUMENT)
}
