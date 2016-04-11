package com.pr0gramm.app.util.decoders;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import java.io.FileInputStream;

import static com.pr0gramm.app.util.AndroidUtility.toFile;

/**
 * Default decoder using android region decoder.
 */
public class AndroidRegionDecoder implements ImageRegionDecoder {

    private BitmapRegionDecoder decoder;
    private Bitmap.Config config;

    public AndroidRegionDecoder(Bitmap.Config config) {
        this.config = config;
    }

    @SuppressLint("NewApi")
    @Override
    public Point init(Context context, Uri uri) throws Exception {
        if (!"file".equals(uri.getScheme()))
            throw new IllegalArgumentException("Must be a file:// uri");

        try (FileInputStream input = new FileInputStream(toFile(uri))) {
            decoder = BitmapRegionDecoder.newInstance(input.getFD(), false);
        }

        return new Point(decoder.getWidth(), decoder.getHeight());
    }

    @Override
    public Bitmap decodeRegion(Rect rect, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = config;
        options.inSampleSize = sampleSize;
        return decoder.decodeRegion(rect, options);
    }

    @Override
    public boolean isReady() {
        return decoder != null && !decoder.isRecycled();
    }

    @Override
    public void recycle() {
        if (decoder != null) {
            decoder.recycle();
        }
    }
}
