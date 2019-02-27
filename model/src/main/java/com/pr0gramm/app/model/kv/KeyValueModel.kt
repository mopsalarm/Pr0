package com.pr0gramm.app.model.kv

import com.squareup.moshi.JsonClass


sealed class PutResult {
    @JsonClass(generateAdapter = true)
    class Version(val version: Int) : PutResult()

    object Conflict : PutResult()
}

sealed class GetResult {
    @JsonClass(generateAdapter = true)
    class Value(val version: Int, val value: ByteArray) : GetResult()

    object NoValue : GetResult()
}
