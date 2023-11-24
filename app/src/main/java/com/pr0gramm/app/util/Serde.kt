package com.pr0gramm.app.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream


object Serde {
    fun serialize(level: Int = Deflater.DEFAULT_COMPRESSION, writer: (out: DataOutput) -> Unit): ByteArray {
        val bytesOut = ByteArrayOutputStream()

        val def = Deflater(level)
        try {
            DeflaterOutputStream(bytesOut, def).use { deflateOut ->
                DataOutputStream(deflateOut).use(writer)
            }
        } finally {
            def.end()
        }

        return bytesOut.toByteArray()
    }

    fun <T> deserialize(bytes: ByteArray, reader: (input: DataInput) -> T): T {
        return DataInputStream(InflaterInputStream(ByteArrayInputStream(bytes))).use(reader)
    }
}
