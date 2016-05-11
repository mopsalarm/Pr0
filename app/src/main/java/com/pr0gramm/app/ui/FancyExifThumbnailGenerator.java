package com.pr0gramm.app.ui;

/**
 * Generates fancy thumbnails from exif data
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.common.io.ByteStreams;
import com.pr0gramm.app.R;
import com.squareup.picasso.Downloader;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import it.sephiroth.android.library.exif2.ExifInterface;

/**
 */
@Singleton
public class FancyExifThumbnailGenerator {
    private final Downloader downloader;
    private final Bitmap maskV;
    private final Bitmap maskH;

    @Inject
    public FancyExifThumbnailGenerator(Context context, Downloader downloader) {
        this.downloader = downloader;
        maskV = BitmapFactory.decodeResource(context.getResources(), R.raw.mask_v);
        maskH = BitmapFactory.decodeResource(context.getResources(), R.raw.mask_h);
    }

    @Nullable
    public Bitmap fancyThumbnail(Uri uri, float aspect) throws IOException {
        byte[] bytes = fetch(uri);

        // almost square? fall back on non fancy normal image
        if (1 / 1.05 < aspect && aspect < 1.05) {
            return decode565(bytes);
        }

        // load exif thumbnail or fall back to square image, if loading fails
        Bitmap low = exifThumbnail(bytes);
        if (low == null)
            return decode565(bytes);

        // decode image as a mutable bitmap
        Bitmap normal = decodeMutableBitmap(bytes);

        // add the alpha mask
        applyAlphaMask(aspect, normal);
        normal.setHasAlpha(true);

        try {
            return compose(aspect, low, normal);
        } finally {
            normal.recycle();
            low.recycle();
        }
    }

    private void applyAlphaMask(float aspect, Bitmap bitmap) {
        Rect baseSquare = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Canvas canvas = new Canvas(bitmap);

        // draw the alpha mask
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(aspect > 1 ? maskH : maskV, null, baseSquare, paint);
    }

    @NonNull
    private Bitmap compose(float aspect, Bitmap low, Bitmap normal) {
        Rect centered = new Rect(0, 0, normal.getWidth(), normal.getHeight());
        int width = centered.width(), height = centered.height();
        if (aspect > 1.0) {
            width = (int) (aspect * height);
            centered.left = (width - height) / 2;
            centered.right = centered.left + height;
        } else {
            height = (int) (width / aspect);
            centered.top = (height - width) / 2;
            centered.bottom = centered.top + width;
        }

        // now generate the result
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Paint paint = new Paint();

        Canvas canvas = new Canvas(result);
        paint.setFlags(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(low, null, new Rect(0, 0, width, height), paint);

        paint.setFlags(paint.getFlags() & ~Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(normal, null, centered, null);
        return result;
    }

    private Bitmap decodeMutableBitmap(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inMutable = true;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private Bitmap exifThumbnail(byte[] bytes) throws IOException {
        ExifInterface exif = new ExifInterface();
        exif.readExif(bytes, ExifInterface.Options.OPTION_ALL);
        return exif.getThumbnailBitmap();
    }

    @SuppressLint("NewApi")
    private byte[] fetch(Uri uri) throws IOException {
        byte[] bytes;
        Downloader.Response response = downloader.load(uri, 0);
        try (InputStream input = response.getInputStream()) {
            bytes = ByteStreams.toByteArray(input);
        }
        return bytes;
    }

    private Bitmap decode565(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
