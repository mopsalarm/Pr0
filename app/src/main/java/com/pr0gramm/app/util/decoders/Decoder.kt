package com.pr0gramm.app.util.decoders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri

interface Decoder {
    fun init(context: Context, uri: Uri): Point?

    fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap?

    fun isReady(): Boolean

    fun recycle()
}