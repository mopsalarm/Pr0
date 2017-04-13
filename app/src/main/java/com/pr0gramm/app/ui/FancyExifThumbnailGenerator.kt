package com.pr0gramm.app.ui


import android.content.Context
import android.graphics.*
import android.net.Uri
import com.google.common.io.ByteStreams
import com.pr0gramm.app.R
import com.squareup.picasso.Downloader
import it.sephiroth.android.library.exif2.ExifInterface
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton


/**
 */
@Singleton
class FancyExifThumbnailGenerator @Inject
constructor(context: Context, private val downloader: Downloader) {
    private val maskV: Bitmap
    private val maskH: Bitmap

    init {
        maskV = BitmapFactory.decodeResource(context.resources, R.raw.mask_v)
        maskH = BitmapFactory.decodeResource(context.resources, R.raw.mask_h)
    }

    fun fancyThumbnail(uri: Uri, aspect: Float): Bitmap? {
        val bytes = fetch(uri)

        // almost square? fall back on non fancy normal image
        if (1 / 1.05 < aspect && aspect < 1.05) {
            return decode565(bytes)
        }

        // load exif thumbnail or fall back to square image, if loading fails
        val low = exifThumbnail(bytes) ?: return decode565(bytes)

        // decode image as a mutable bitmap
        val normal = decodeMutableBitmap(bytes)

        // add the alpha mask
        applyAlphaMask(aspect, normal)
        normal.setHasAlpha(true)

        try {
            return compose(aspect, low, normal)
        } finally {
            normal.recycle()
            low.recycle()
        }
    }

    private fun applyAlphaMask(aspect: Float, bitmap: Bitmap) {
        val baseSquare = Rect(0, 0, bitmap.width, bitmap.height)
        val canvas = Canvas(bitmap)

        // draw the alpha mask
        val paint = Paint()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(if (aspect > 1) maskH else maskV, null, baseSquare, paint)
    }

    private fun compose(aspect: Float, low: Bitmap, normal: Bitmap): Bitmap {
        val centered = Rect(0, 0, normal.width, normal.height)
        var width = centered.width()
        var height = centered.height()
        if (aspect > 1.0) {
            width = (aspect * height).toInt()
            centered.left = (width - height) / 2
            centered.right = centered.left + height
        } else {
            height = (width / aspect).toInt()
            centered.top = (height - width) / 2
            centered.bottom = centered.top + width
        }

        // now generate the result
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val paint = Paint()

        val canvas = Canvas(result)
        paint.flags = Paint.FILTER_BITMAP_FLAG
        canvas.drawBitmap(low, null, Rect(0, 0, width, height), paint)

        paint.flags = paint.flags and Paint.FILTER_BITMAP_FLAG.inv()
        canvas.drawBitmap(normal, null, centered, null)
        return result
    }

    private fun decodeMutableBitmap(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inMutable = true
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun exifThumbnail(bytes: ByteArray): Bitmap? {
        ByteArrayInputStream(bytes).use { inputStream ->
            val ei = ExifInterface()
            ei.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
            return ei.thumbnailBitmap
        }
    }

    private fun fetch(uri: Uri): ByteArray {
        val response = downloader.load(uri, 0)
        return response.inputStream.use { input -> ByteStreams.toByteArray(input) }
    }

    private fun decode565(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
