package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toFile
import com.pr0gramm.app.util.isLocalFile
import java.io.FileInputStream

/**
 * Default decoder using android region decoder.
 */
class AndroidRegionDecoder(private val config: Bitmap.Config) : Decoder {
    private var decoder: BitmapRegionDecoder? = null

    override fun init(context: Context, uri: Uri): Point {
        if (!uri.isLocalFile)
            throw IllegalArgumentException("Must be a file:// uri")

        val decoder = FileInputStream(uri.toFile()).use { input ->
            BitmapRegionDecoder.newInstance(input.fd, false)
        }

        this.decoder = decoder
        return Point(decoder.width, decoder.height)
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = config
        options.inSampleSize = sampleSize
        return decoder?.decodeRegion(rect, options)
    }

    override fun isReady(): Boolean {
        return decoder?.let { !it.isRecycled } ?: false
    }

    override fun recycle() {
        decoder?.recycle()
        decoder = null
    }
}
