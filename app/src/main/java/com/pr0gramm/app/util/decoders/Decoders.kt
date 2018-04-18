package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.squareup.picasso.Downloader
import org.slf4j.LoggerFactory

object Decoders {
    @JvmStatic
    fun newFancyRegionDecoder(downloader: Downloader): ImageRegionDecoder {
        val decoders = mutableListOf<Decoder>(
                SimpleRegionDecoder(Bitmap.Config.RGB_565),
                SimpleRegionDecoder(Bitmap.Config.ARGB_8888))

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            decoders.addAll(0, listOf(
                    AndroidRegionDecoder(Bitmap.Config.RGB_565),
                    AndroidRegionDecoder(Bitmap.Config.ARGB_8888)))
        }

        return adapt(DownloadingRegionDecoder(downloader, FallbackRegionDecoder.chain(decoders)))
    }

    private fun adapt(dec: Decoder): ImageRegionDecoder {
        val logger = LoggerFactory.getLogger("Decoder")

        return object : ImageRegionDecoder {
            @Volatile
            private var recycled: Boolean = false

            override fun init(context: Context, uri: Uri): Point {
                logger.info("Decoder.init(context, {}) called", uri)
                return dec.takeUnless { recycled }?.init(context, uri) ?: Point(64, 64)
            }

            override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
                logger.info("Decoder.decodeRegion({}, {}) called", sRect, sampleSize)
                val fallback by lazy { Bitmap.createBitmap(sRect.width(), sRect.height(), Bitmap.Config.RGB_565) }
                if (recycled) {
                    return fallback
                }

                return dec.takeUnless { recycled }?.decodeRegion(sRect, sampleSize) ?: fallback
            }

            override fun isReady(): Boolean {
                return !recycled && dec.isReady()
            }

            override fun recycle() {
                logger.info("Decoder.recycle() called")

                recycled = true
                dec.recycle()
            }
        }
    }
}
