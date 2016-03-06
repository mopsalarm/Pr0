package com.pr0gramm.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

import com.pr0gramm.app.R;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 */
@Singleton
public class FancyThumbnailGenerator {
    private final Bitmap maskV;
    private final Bitmap maskH;

    @Inject
    public FancyThumbnailGenerator(Context context) {
        maskV = BitmapFactory.decodeResource(context.getResources(), R.raw.mask_v);
        maskH = BitmapFactory.decodeResource(context.getResources(), R.raw.mask_h);
    }

    @Nullable
    public Bitmap fancyThumbnail(Bitmap input, float aspect) {
        // almost square? fall back on non fancy normal image
        if (1 / 1.05 < aspect && aspect < 1.05) {
            return input;
        }

        // convert image to a mutable bitmap
        Bitmap thumbnail = input.copy(Bitmap.Config.ARGB_8888, true);

        // add the alpha mask
        applyAlphaMask(aspect, thumbnail);
        thumbnail.setHasAlpha(true);
        return thumbnail;
    }

    private void applyAlphaMask(float aspect, Bitmap bitmap) {
        Rect baseSquare = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Canvas canvas = new Canvas(bitmap);

        // draw the alpha mask
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(aspect > 1 ? maskH : maskV, null, baseSquare, paint);
    }
}
