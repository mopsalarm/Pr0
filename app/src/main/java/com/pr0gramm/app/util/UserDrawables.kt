package com.pr0gramm.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import kotlin.math.absoluteValue

/**
 * Creates drawables for users based on their name and id.
 */
class UserDrawables(private val context: Context) {
    fun drawable(message: Message): Drawable {
        return drawable(message.name)
    }

    fun drawable(name: String): Drawable {
        val faceConfig = Face.forValue(name)
        return BitmapDrawable(context.resources, paint(faceConfig, 128, 128))
    }

    fun makeSenderBitmap(message: Message, width: Int, height: Int): Bitmap {
        val faceConfig = Face.forValue(message.name)
        return paint(faceConfig, width, height)
    }

    private fun paint(faceConfig: Face.Config, width: Int, height: Int): Bitmap {
        // create a bitmap for output
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)

        // fill background
        canvas.drawColor(faceConfig.color)

        // paint every layer into the full canvas
        val paddingX = width / 10f
        val paddingY = height / 10f
        val layerSize = RectF(
                paddingX,
                paddingY,
                width.toFloat() - paddingX,
                height.toFloat() - paddingY
        )

        val layerPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        for (imageId in listOf(faceConfig.eyes, faceConfig.mouth, faceConfig.nose)) {
            val layer = BitmapFactory.decodeResource(context.resources, imageId)
            canvas.drawBitmap(layer, null, layerSize, layerPaint)
            layer.recycle()
        }

        return bitmap
    }
}

private object Face {
    private val eyes = listOf(
            R.drawable.eyes1,
            R.drawable.eyes2,
            R.drawable.eyes3,
            R.drawable.eyes4,
            R.drawable.eyes5,
            R.drawable.eyes6,
            R.drawable.eyes7,
            R.drawable.eyes9,
            R.drawable.eyes10
    )

    private val mouths = listOf(
            R.drawable.mouth1,
            R.drawable.mouth3,
            R.drawable.mouth5,
            R.drawable.mouth6,
            R.drawable.mouth7,
            R.drawable.mouth9,
            R.drawable.mouth10,
            R.drawable.mouth11
    )

    private val noses = listOf(
            R.drawable.nose2,
            R.drawable.nose3,
            R.drawable.nose4,
            R.drawable.nose5,
            R.drawable.nose6,
            R.drawable.nose7,
            R.drawable.nose8,
            R.drawable.nose9
    )

    private val colors = listOf(
            0xffe57373.toInt(),
            0xfff06292.toInt(),
            0xffba68c8.toInt(),
            0xff9575cd.toInt(),
            0xff7986cb.toInt(),
            0xff64b5f6.toInt(),
            0xff4fc3f7.toInt(),
            0xff4dd0e1.toInt(),
            0xff4db6ac.toInt(),
            0xff81c784.toInt(),
            0xffaed581.toInt(),
            0xffff8a65.toInt(),
            0xffd4e157.toInt(),
            0xffffd54f.toInt(),
            0xffffb74d.toInt(),
            0xffa1887f.toInt(),
            0xff90a4ae.toInt()
    )

    fun forValue(input: String): Config {
        fun <T> List<T>.pick(key: String): T {
            val hashCode = key.repeat(8).hashCode() + key.length
            val index = hashCode % size
            return getOrNull(index.absoluteValue) ?: get(0)
        }

        return Config(
                color = colors.pick("color:$input"),
                eyes = eyes.pick("eyes:$input"),
                mouth = mouths.pick("mouth:$input"),
                nose = noses.pick("nose:$input"))
    }

    class Config(
            @ColorInt val color: Int,
            @DrawableRes val eyes: Int,
            @DrawableRes val mouth: Int,
            @DrawableRes val nose: Int)
}
