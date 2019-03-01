package com.pr0gramm.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.amulyakhare.textdrawable.TextDrawable
import com.amulyakhare.textdrawable.util.ColorGenerator
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api

/**
 * Creates drawables for users based on their name and id.
 */
class UserDrawables(context: Context) {
    private val shapes = TextDrawable.builder().beginConfig()
            .textColor(ContextCompat.getColor(context, R.color.feed_background))
            .fontSize(context.dip2px(18))
            .bold()
            .endConfig()

    fun drawable(message: Api.Message): TextDrawable {
        return drawable(message.senderId, message.name)
    }

    fun drawable(name: String): TextDrawable {
        return drawable(name, name)
    }

    private fun drawable(key: Any, name: String): TextDrawable {
        val color = ColorGenerator.MATERIAL.getColor(key)
        return shapes.buildRound(iconText(name), color)
    }

    fun makeSenderBitmap(message: Api.Message, width: Int, height: Int): Bitmap {
        val drawable = drawable(message)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun iconText(name: String): String {
        return if (name.length == 1) {
            name.substring(0, 1).toUpperCase()
        } else {
            name.substring(0, 1).toUpperCase() + name.substring(1, 2).toLowerCase()
        }
    }
}
