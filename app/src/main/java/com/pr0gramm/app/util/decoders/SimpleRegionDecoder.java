package com.pr0gramm.app.util.decoders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import static com.pr0gramm.app.util.AndroidUtility.toFile;

/**
 */
public class SimpleRegionDecoder implements ImageRegionDecoder {
    private Bitmap bitmap;
    private Bitmap.Config config;

    public SimpleRegionDecoder(Bitmap.Config config) {
        this.config = config;
    }

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        if ("file".equals(uri.getScheme())) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = config;

            // load the image
            bitmap = BitmapFactory.decodeFile(toFile(uri).getPath(), options);
            return new Point(bitmap.getWidth(), bitmap.getHeight());
        }

        throw new IllegalArgumentException("Can only process images from the local filesystem");
    }

    @Override
    public Bitmap decodeRegion(Rect rect, int sampleSize) {
        Matrix matrix = new Matrix();

        if (sampleSize > 1) {
            // only sample if needed
            float factor = 1.f / (1 << Math.max(0, sampleSize - 1));
            matrix.setScale(factor, factor);
        }

        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height(), matrix, true);
    }

    @Override
    public boolean isReady() {
        return bitmap != null;
    }

    @Override
    public void recycle() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }
}
