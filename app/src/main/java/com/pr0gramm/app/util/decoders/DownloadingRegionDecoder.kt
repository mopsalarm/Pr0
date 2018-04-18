package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.google.common.base.Preconditions.checkState
import com.google.common.io.ByteStreams
import com.pr0gramm.app.util.AndroidUtility.toFile
import com.squareup.picasso.Downloader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * This decoder first downloads the image before starting to decode it.
 */
class DownloadingRegionDecoder(private val downloader: Downloader, private val decoder: Decoder) : Decoder {
    private var imageFile: File? = null
    private var deleteImageOnRecycle: Boolean = false

    override fun init(context: Context, uri: Uri): Point? {
        checkState(imageFile == null, "Can not call init twice.")

        val file = if ("file" == uri.scheme) {
            toFile(uri).also { imageFile = it }
        } else {
            val file = File.createTempFile("image", ".tmp", context.cacheDir)
            imageFile = file
            deleteImageOnRecycle = true

            try {
                downloadTo(uri, file)
                file

            } catch (error: IOException) {
                logger.warn("Could not download image to temp file")

                if (!file.delete())
                    logger.warn("Could not delete file")

                // re-raise exception
                throw IOException("Could not download image to temp file", error)
            }
        }

        return decoder.init(context, Uri.fromFile(file))
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap? {
        try {
            return decoder.decodeRegion(rect, sampleSize)

        } catch (oom: OutOfMemoryError) {
            throw RuntimeException(oom)
        }
    }

    override fun isReady(): Boolean {
        return imageFile != null && decoder.isReady()
    }

    override fun recycle() {
        try {
            decoder.recycle()
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        imageFile?.let { file ->
            if (deleteImageOnRecycle && file.exists()) {
                file.delete()
            }
        }
    }

    fun finalize() {
        cleanup()
    }

    private fun downloadTo(uri: Uri, imageFile: File) {
        val req = okhttp3.Request.Builder().url(uri.toString()).build()

        // download to temp file. not nice, but useful :/
        downloader.load(req).body()?.byteStream()?.use { inputStream ->
            FileOutputStream(imageFile).use { output ->
                ByteStreams.copy(inputStream, output)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("DownloadingRegionDecoder")
    }
}
