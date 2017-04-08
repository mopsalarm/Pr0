package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.google.common.base.Preconditions.checkState
import org.slf4j.LoggerFactory
import java.util.*

/**
 * This decoder has a reference to two decoders and initializes and uses the fallback
 * decoder if the firstone encounters an error.
 */
class FallbackRegionDecoder(private val decoder: ImageRegionDecoder,
                            private var fallbackSupplier: () -> ImageRegionDecoder) : ImageRegionDecoder {

    private var fallback: ImageRegionDecoder? = null

    override fun init(context: Context, uri: Uri): Point {
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

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        fallback?.let {
            return it.decodeRegion(rect, sampleSize)
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
        return current().isReady
    }

    override fun recycle() {
        current().recycle()
    }

    private fun current(): ImageRegionDecoder {
        return fallback ?: decoder
    }

    private fun switchToFallback(): ImageRegionDecoder {
        try {
            decoder.recycle()
        } catch (ignored: Exception) {
        }

        checkState(fallback == null, "Fallback already assigned")

        return fallbackSupplier().also {
            this.fallback = it
            logger.info("Using fallback decoder {} now.", it)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("FallbackRegionDecoder")

        private fun makeAfterInitFallbackSupplier(context: Context, uri: Uri, originalFallbackFactory: () -> ImageRegionDecoder): () -> ImageRegionDecoder {
            return {
                try {
                    originalFallbackFactory().apply { init(context, uri) }

                } catch (error: Exception) {
                    throw RuntimeException("Error initializing fallback decoder", error)
                }
            }
        }

        fun chain(start: ImageRegionDecoder, vararg fallbacks: ImageRegionDecoder): ImageRegionDecoder {
            if (fallbacks.isEmpty()) {
                return start
            } else {
                val tail = Arrays.copyOfRange(fallbacks, 1, fallbacks.size)
                return FallbackRegionDecoder(start, { chain(fallbacks[0], *tail) })
            }
        }
    }
}
