package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.pr0gramm.app.util.AndroidUtility.toFile

/**
 * Decodes the complete image and then cuts out the region we want.
 */
class SimpleRegionDecoder(private val config: Bitmap.Config) : Decoder {
    private var bitmap: Bitmap? = null

    override fun init(context: Context, uri: Uri): Point {
        if (uri.scheme == "file") {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = config
            }

            // load the image
            BitmapFactory.decodeFile(toFile(uri).path, options).let {
                bitmap = it
                return Point(it.width, it.height)
            }
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
