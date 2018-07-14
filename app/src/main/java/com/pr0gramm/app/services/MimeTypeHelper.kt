package com.pr0gramm.app.services

import com.pr0gramm.app.util.readSimple
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Guesses the mime type of a file or input stream
 */
object MimeTypeHelper {
    fun guess(bytes: ByteArray): String? {
        if (indexOf(bytes, MAGIC_JPEG) == 0)
            return "image/jpeg"

        if (indexOf(bytes, MAGIC_GIF) == 0)
            return "image/gif"

        if (indexOf(bytes, MAGIC_PNG) == 0)
            return "image/png"

        if (MAGIC_MP4.any { q -> indexOf(bytes, q) != -1 })
            return "video/mp4"

        if (MAGIC_WEBM.any { q -> indexOf(bytes, q) != -1 })
            return "video/webm"

        return null
    }

    fun guess(file: File): String? {
        try {
            return FileInputStream(file).use { input -> guess(input) }
        } catch(err: IOException) {
            return guessFromFileExtension(file)
        }
    }

    fun guessFromFileExtension(file: File): String? {
        return EXTENSION_TO_TYPE[file.extension.toLowerCase()]
    }

    fun guess(input: InputStream): String? {
        return guess(ByteArray(512).also { input.readSimple(it) })
    }

    fun extension(type: String): String? {
        return TYPE_TO_EXTENSION[type]
    }

    private val TYPE_TO_EXTENSION = mapOf(
            "image/jpeg" to "jpeg",
            "image/png" to "png",
            "image/gif" to "gif",
            "video/webm" to "webm",
            "video/mp4" to "mp4")

    private val EXTENSION_TO_TYPE = mapOf(
            "jpeg" to "image/jpeg",
            "jpg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "webm" to "video/webm",
            "mp4" to "video/mp4")

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

    fun indexOf(array: ByteArray, target: ByteArray): Int {
        if (target.isEmpty()) {
            return 0
        }

        outer@ for (i in 0 until array.size - target.size + 1) {
            for (j in target.indices) {
                if (array[i + j] != target[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
