package com.pr0gramm.app.ui

import android.graphics.Bitmap
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.squareup.picasso.Transformation

class BlurTransformation(private val radius: Int) : Transformation {
    private val logger = Logger("BlurTransformation")

    override fun key(): String = "blur:$radius"

    override fun transform(source: Bitmap): Bitmap {
        try {
            return logger.time("Blur image of size ${source.width}x${source.height} with radius $radius") {
                source.blur(radius)
            }
        } finally {
            source.recycle()
        }
    }
}
