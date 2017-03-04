package com.pr0gramm.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.v4.content.ContextCompat;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;

/**
 * Creates drawables for users based on their name and id.
 */
public final class SenderDrawableProvider {
    private final TextDrawable.IShapeBuilder shapes;

    public SenderDrawableProvider(Context context) {
        this.shapes = TextDrawable.builder().beginConfig()
                .textColor(ContextCompat.getColor(context, R.color.feed_background))
                .fontSize(AndroidUtility.dp(context, 24))
                .bold()
                .endConfig();
    }

    public TextDrawable makeSenderDrawable(Api.Message message) {
        int color = ColorGenerator.MATERIAL.getColor(message.senderId());
        return shapes.buildRect(iconText(message.name()), color);
    }

    public Bitmap makeSenderBitmap(Api.Message message, int width, int height) {
        TextDrawable drawable = makeSenderDrawable(message);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private static String iconText(String name) {
        if (name.length() == 1) {
            return name.substring(0, 1).toUpperCase();
        } else {
            return name.substring(0, 1).toUpperCase() + name.substring(1, 2).toLowerCase();
        }
    }
}
