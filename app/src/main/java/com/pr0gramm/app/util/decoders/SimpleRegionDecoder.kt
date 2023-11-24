package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toFile
import com.pr0gramm.app.util.isLocalFile

/**
 * Decodes the complete image and then cuts out the region we want.
 */
class SimpleRegionDecoder(private val config: Bitmap.Config) : Decoder {
    private var bitmap: Bitmap? = null

    override fun init(context: Context, uri: Uri): Point {
        if (uri.isLocalFile) {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = config

            // load the image
            val bitmap = BitmapFactory.decodeFile(uri.toFile().path, options)
            this.bitmap = bitmap
            return Point(bitmap.width, bitmap.height)
        }

        throw IllegalArgumentException("Can only process images from the local filesystem")
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        val matrix = Matrix()

        if (sampleSize > 1) {
            // only sample if needed
            val factor = 1f / (1 shl Math.max(0, sampleSize - 1))
            matrix.setScale(factor, factor)
        }

        val bitmap = this.bitmap
                ?: throw IllegalStateException("Tried to decode region with bitmap not set.")

        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height(), matrix, true)
    }

    override fun isReady(): Boolean {
        return bitmap != null
    }

    override fun recycle() {
        bitmap?.recycle()
        bitmap = null
    }
}
