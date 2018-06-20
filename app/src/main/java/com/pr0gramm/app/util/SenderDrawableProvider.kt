package com.pr0gramm.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.support.v4.content.ContextCompat
import com.amulyakhare.textdrawable.TextDrawable
import com.amulyakhare.textdrawable.util.ColorGenerator
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api

/**
 * Creates drawables for users based on their name and id.
 */
class SenderDrawableProvider(context: Context) {
    private val shapes = TextDrawable.builder().beginConfig()
            .textColor(ContextCompat.getColor(context, R.color.feed_background))
            .fontSize(AndroidUtility.dp(context, 24))
            .bold()
            .endConfig()

    fun makeSenderDrawable(message: Api.Message): TextDrawable {
        val color = ColorGenerator.MATERIAL.getColor(message.senderId)
        return shapes.buildRound(iconText(message.name), color)
    }

    fun makeSenderBitmap(message: Api.Message, width: Int, height: Int): Bitmap {
        val drawable = makeSenderDrawable(message)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun iconText(name: String): String {
        if (name.length == 1) {
            return name.substring(0, 1).toUpperCase()
        } else {
            return name.substring(0, 1).toUpperCase() + name.substring(1, 2).toLowerCase()
        }
    }
}
