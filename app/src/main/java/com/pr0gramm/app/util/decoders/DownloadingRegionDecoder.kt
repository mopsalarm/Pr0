package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.io.FileEntry
import com.pr0gramm.app.services.Track.context
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.isLocalFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * This decoder first downloads the image before starting to decode it.
 */
class DownloadingRegionDecoder(private val cache: Cache, private val decoder: Decoder) : Decoder {
    private val logger = Logger("DownloadingRegionDecoder")

    private var imageFileRef: FileRef? = null

    override fun init(context: Context, uri: Uri): Point? {
        require(imageFileRef == null) { "Can not call init twice." }

        if (uri.isLocalFile) {
            imageFileRef = FileRef(uri.toFile(), shared = true)
            return decoder.init(context, uri)

        } else {
            try {
                val ref = resolveToFile(uri)
                imageFileRef = ref

                return decoder.init(context, ref.file.toUri())

            } catch (error: IOException) {
                logger.warn { "Could not download image to temp file" }

                // re-raise exception
                throw IOException("Could not download image to temp file", error)
            }
        }
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap? {
        try {
            return decoder.decodeRegion(rect, sampleSize)

        } catch (oom: OutOfMemoryError) {
            throw RuntimeException(oom)
        }
    }

    override fun isReady(): Boolean {
        return imageFileRef != null && decoder.isReady()
    }

    override fun recycle() {
        try {
            decoder.recycle()
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        imageFileRef?.let { ref ->
            if (!ref.shared) {
                ref.file.delete()
            }
        }
    }

    @Suppress("unused")
    fun finalize() {
        cleanup()
    }

    private fun resolveToFile(uri: Uri): FileRef {
        cache.get(uri).use { entry ->
            if (entry is FileEntry)
                return FileRef(entry.file, shared = true)

            val file = File.createTempFile("image", ".tmp", context.cacheDir)
            try {
                FileOutputStream(file).use { output ->
                    entry.inputStreamAt(0).use { inputStream ->
                        inputStream.copyTo(output, bufferSize = 1024 * 64)
                    }
                }

                return FileRef(file, shared = false)
            } catch (err: Exception) {
                file.delete()
                throw err
            }
        }
    }

    class FileRef(val file: File, val shared: Boolean)
}
