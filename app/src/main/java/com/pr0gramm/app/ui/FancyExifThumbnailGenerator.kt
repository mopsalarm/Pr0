package com.pr0gramm.app.ui


import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.applyCanvas
import androidx.core.net.toFile
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.time
import com.pr0gramm.app.util.isLocalFile
import com.squareup.picasso.Downloader
import okhttp3.Request
import java.io.ByteArrayInputStream


/**
 */
class FancyExifThumbnailGenerator(context: Application, private val downloader: Downloader) {
    private val logger = Logger("FancyExifThumbnailGenerator")

    private val maskV by lazy { BitmapFactory.decodeResource(context.resources, R.raw.mask_v) }
    private val maskH by lazy { BitmapFactory.decodeResource(context.resources, R.raw.mask_h) }

    private val zero = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

    fun fancyThumbnail(uri: Uri, aspect: Float): Bitmap {
        return fancyThumbnail0(uri, aspect) ?: zero
    }

    private fun fancyThumbnail0(uri: Uri, aspect: Float): Bitmap? = logger.time("Building fancy thumbnail") {
        val bytes = fetch(uri) ?: return null

        // almost square? fall back on non fancy normal image
        if (1 / 1.05 < aspect && aspect < 1.05) {
            return decodeToBitmap(bytes)
        }

        // load exif thumbnail or fall back to square image, if loading fails
        val low = exifThumbnail(bytes) ?: return decodeToBitmap(bytes)

        // decode the image mutable so that we can paint on it
        val normal = decodeMutableBitmap(bytes)

        try {
            // add the alpha mask
            applyAlphaMask(aspect, normal)
            normal.setHasAlpha(true)

            return compose(aspect, low, normal)

        } finally {
            normal.recycle()
            low.recycle()
        }
    }

    private fun applyAlphaMask(aspect: Float, bitmap: Bitmap) {
        val baseSquare = Rect(0, 0, bitmap.width, bitmap.height)

        bitmap.applyCanvas {
            // draw the alpha mask
            val paint = Paint()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            drawBitmap(if (aspect > 1) maskH else maskV, null, baseSquare, paint)
        }
    }

    private fun compose(aspect: Float, low: Bitmap, normal: Bitmap): Bitmap {
        val centered = Rect(0, 0, normal.width, normal.height)
        var width = centered.width()
        var height = centered.height()
        if (aspect > 1.0) {
            width = (aspect * height).toInt().coerceAtLeast(1)
            centered.left = (width - height) / 2
            centered.right = centered.left + height
        } else {
            height = (width / aspect).toInt().coerceAtLeast(1)
            centered.top = (height - width) / 2
            centered.bottom = centered.top + width
        }

        // now generate the result
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        return result.applyCanvas {
            val paint = Paint()

            paint.flags = Paint.FILTER_BITMAP_FLAG
            drawBitmap(low, null, Rect(0, 0, width, height), paint)

            paint.flags = paint.flags and Paint.FILTER_BITMAP_FLAG.inv()
            drawBitmap(normal, null, centered, null)
        }
    }

    private fun decodeMutableBitmap(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inMutable = true
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun exifThumbnail(bytes: ByteArray): Bitmap? {
        ByteArrayInputStream(bytes).use { inputStream ->
            val exif = androidx.exifinterface.media.ExifInterface(inputStream)
            return exif.thumbnailBitmap
        }
    }

    private fun fetch(uri: Uri): ByteArray? {
        if (uri.isLocalFile) {
            return uri.toFile().readBytes()
        }

        if (uri.scheme != "http" && uri.scheme != "https")
            return null

        val response = downloader.load(Request.Builder().url(uri.toString()).build())
        return response.body?.use { body -> body.bytes().takeIf { it.isNotEmpty() } }
    }

    private fun decodeToBitmap(bytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
