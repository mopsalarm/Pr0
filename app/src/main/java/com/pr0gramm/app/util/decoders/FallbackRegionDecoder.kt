package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import com.pr0gramm.app.util.logger

/**
 * This decoder has a reference to two decoders and initializes and uses the fallback
 * decoder if the firstone encounters an error.
 */
class FallbackRegionDecoder(private val decoder: Decoder,
                            private var fallbackSupplier: () -> Decoder) : Decoder {

    private var fallback: Decoder? = null

    override fun init(context: Context, uri: Uri): Point? {
        try {
            val result = decoder.init(context, uri)

            // everything is good, lets update our fallback decoder supplier.
            this.fallbackSupplier = makeAfterInitFallbackSupplier(context, uri, fallbackSupplier)
            return result

        } catch (error: Exception) {
            logger.info("Error initializing primary decoder")

            // something went wrong, switch to fallback.
            return switchToFallback().init(context, uri)
        }
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap? {
        fallback?.let { fallback ->
            return fallback.decodeRegion(rect, sampleSize)
        }

        try {
            // decode normally
            val result = decoder.decodeRegion(rect, sampleSize)
            if (result != null)
                return result

        } catch (error: RuntimeException) {
            logger.info("Error in primary decoder.", error)
        }

        // okay, there was an error, lets go to fallback
        return switchToFallback().decodeRegion(rect, sampleSize)
    }

    override fun isReady(): Boolean {
        return current().isReady()
    }

    override fun recycle() {
        current().recycle()
    }

    private fun current(): Decoder {
        return fallback ?: decoder
    }

    private fun switchToFallback(): Decoder {
        try {
            decoder.recycle()
        } catch (ignored: Exception) {
        }

        require(fallback == null) {
            "Fallback already assigned"
        }

        return fallbackSupplier().also {
            this.fallback = it
            logger.info("Using fallback decoder {} now.", it)
        }
    }

    companion object {
        private val logger = logger("FallbackRegionDecoder")

        private fun makeAfterInitFallbackSupplier(context: Context, uri: Uri, originalFallbackFactory: () -> Decoder): () -> Decoder {
            return {
                try {
                    originalFallbackFactory().apply { init(context, uri) }

                } catch (error: Exception) {
                    throw RuntimeException("Error initializing fallback decoder", error)
                }
            }
        }

        fun chain(chain: List<Decoder>): Decoder {
            if (chain.size == 1) {
                return chain.first()
            } else {
                val tail = chain.drop(1)
                return FallbackRegionDecoder(chain.first(), { chain(tail) })
            }
        }
    }
}
