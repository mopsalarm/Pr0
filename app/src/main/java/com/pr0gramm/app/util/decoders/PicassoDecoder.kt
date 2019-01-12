package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import java.io.IOException

/**
 * Decodes image using picasso.
 */
class PicassoDecoder(private val tag: String, private val picasso: Picasso) : ImageDecoder {
    override fun decode(context: Context, uri: Uri): Bitmap {
        try {
            val bitmap = picasso.load(uri).tag(tag)
                    .config(Bitmap.Config.RGB_565)
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .get()

            return bitmap ?: throw IOException("Could not load bitmap")

        } catch (error: OutOfMemoryError) {
            throw RuntimeException(error)
        }
    }

    companion object {
        fun factory(tag: String, picasso: Picasso): DecoderFactory<ImageDecoder> {
            return DecoderFactory { PicassoDecoder(tag, picasso) }
        }
    }
}
