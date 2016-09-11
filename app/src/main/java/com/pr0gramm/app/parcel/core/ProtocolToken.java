package com.pr0gramm.app.parcel.core;

import com.google.gson.stream.JsonToken;

/**
 */
enum ProtocolToken {
    NULL(JsonToken.NULL),
    STRING(JsonToken.STRING),

    BYTE(JsonToken.NUMBER),
    INTEGER(JsonToken.NUMBER),
    LONG(JsonToken.NUMBER),
    FLOAT(JsonToken.NUMBER),
    DOUBLE(JsonToken.NUMBER),

    ARRAY_BEGIN(JsonToken.BEGIN_ARRAY),
    ARRAY_END(JsonToken.END_ARRAY),

    OBJECT_BEGIN(JsonToken.BEGIN_OBJECT),
    OBJECT_END(JsonToken.END_OBJECT),
    NAME(JsonToken.NAME),
    NAME_REF(JsonToken.NAME),

    BOOLEAN_TRUE(JsonToken.BOOLEAN),
    BOOLEAN_FALSE(JsonToken.BOOLEAN),

    DOCUMENT_END(JsonToken.END_DOCUMENT);

    final JsonToken token;

    ProtocolToken(JsonToken token) {
        this.token = token;
    }
}
