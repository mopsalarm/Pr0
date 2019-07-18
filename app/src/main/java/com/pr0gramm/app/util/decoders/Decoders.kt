package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.pr0gramm.app.Logger
import com.pr0gramm.app.io.Cache

object Decoders {
    fun regionDecoderFactory(cache: Cache): DecoderFactory<ImageRegionDecoder> {
        return DecoderFactory {

            val decoders = mutableListOf<Decoder>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // try to decode for hardware first
                decoders += AndroidRegionDecoder(Bitmap.Config.HARDWARE)
            }

            decoders += AndroidRegionDecoder(Bitmap.Config.ARGB_8888)
            decoders += AndroidRegionDecoder(Bitmap.Config.RGB_565)

            decoders += SimpleRegionDecoder(Bitmap.Config.ARGB_8888)
            decoders += SimpleRegionDecoder(Bitmap.Config.RGB_565)

            adapt(DownloadingRegionDecoder(cache, FallbackRegionDecoder.chain(decoders)))
        }
    }

    private fun adapt(dec: Decoder): ImageRegionDecoder {
        val logger = Logger("Decoder")

        return object : ImageRegionDecoder {
            @Volatile
            private var recycled: Boolean = false

            override fun init(context: Context, uri: Uri): Point {
                logger.debug { "Decoder.init(context, $uri) called" }
                return dec.takeUnless { recycled }?.init(context, uri) ?: Point(64, 64)
            }

            override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
                logger.debug { "Decoder.decodeRegion($sRect, $sampleSize) called" }
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
                logger.debug { "Decoder.recycle() called" }

                recycled = true
                dec.recycle()
            }
        }
    }
}
