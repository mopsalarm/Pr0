package com.pr0gramm.app.services

import com.google.common.base.Optional
import com.google.common.io.ByteStreams
import com.google.common.primitives.Bytes
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Guesses the mime type of a file or input stream
 */
object MimeTypeHelper {
    fun guess(bytes: ByteArray): Optional<String> {
        if (Bytes.indexOf(bytes, MAGIC_JPEG) == 0)
            return Optional.of("image/jpeg")

        if (Bytes.indexOf(bytes, MAGIC_GIF) == 0)
            return Optional.of("image/gif")

        if (Bytes.indexOf(bytes, MAGIC_PNG) == 0)
            return Optional.of("image/png")

        if (MAGIC_MP4.any { q -> Bytes.indexOf(bytes, q) != -1 })
            return Optional.of("video/mp4")

        if (MAGIC_WEBM.any { q -> Bytes.indexOf(bytes, q) != -1 })
            return Optional.of("video/webm")

        return Optional.absent<String>()
    }

    @Throws(IOException::class)
    fun guess(file: File): Optional<String> {
        return FileInputStream(file).use { input -> guess(input) }
    }

    @Throws(IOException::class)
    fun guess(input: InputStream): Optional<String> {
        val bytes = ByteArray(512)
        ByteStreams.read(input, bytes, 0, bytes.size)

        return guess(bytes)
    }

    fun extension(type: String): Optional<String> {
        return Optional.fromNullable(EXTENSIONS[type])
    }

    private val EXTENSIONS = mapOf(
            "image/jpeg" to "jpeg",
            "image/png" to "png",
            "image/gif" to "gif",
            "video/webm" to "webm",
            "video/mp4" to "mp4")

    private val MAGIC_PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    private val MAGIC_JPEG = byteArrayOf(0xff.toByte(), 0xd8.toByte())

    private val MAGIC_GIF = "GIF89a".toByteArray()

    private val MAGIC_MP4 = listOf(
            "ftypmp42".toByteArray(),
            "moov".toByteArray(),
            "isom".toByteArray())

    private val MAGIC_WEBM = listOf(
            byteArrayOf(0x1a, 0x54, 0xdf.toByte(), 0xa3.toByte()),
            "webm".toByteArray())

}
