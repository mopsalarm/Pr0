package com.pr0gramm.app.util

import java.io.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream


object Serde {
    fun serialize(writer: (out: DataOutput) -> Unit): ByteArray {
        val bytesOut = ByteArrayOutputStream()

        DeflaterOutputStream(bytesOut, Deflater(Deflater.DEFAULT_COMPRESSION)).use { deflateOut ->
            DataOutputStream(deflateOut).use(writer)
        }

        return bytesOut.toByteArray()
    }

    fun <T> deserialize(bytes: ByteArray, reader: (input: DataInput) -> T): T {
        return DataInputStream(InflaterInputStream(ByteArrayInputStream(bytes))).use(reader)
    }
}
