package com.pr0gramm.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 */
public class ImageDecoders {
    private ImageDecoders() {
    }

    public static class PicassoDecoder implements ImageDecoder {
        private String tag;
        private Picasso picasso;

        public PicassoDecoder(String tag, Picasso picasso) {
            this.tag = tag;
            this.picasso = picasso;
        }

        @Override
        public Bitmap decode(Context context, Uri uri) throws Exception {
            return picasso
                    .load(uri)
                    .tag(tag)
                    .config(Bitmap.Config.RGB_565)
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .get();
        }
    }

    public static class PicassoRegionDecoder implements ImageRegionDecoder {
        private static final Logger logger = LoggerFactory.getLogger("PicassoRegionDecoder");

        private static final Object DECODER_LOCK = new Object();
        private final Downloader downloader;
        private BitmapRegionDecoder decoder;

        public PicassoRegionDecoder(Downloader downloader) {
            this.downloader = downloader;
        }

        @Override
        public Point init(Context context, Uri uri) throws Exception {
            if ("file".equals(uri.getScheme())) {
                this.decoder = BitmapRegionDecoder.newInstance(uri.getPath(), false);
            } else {
                InputStream inputStream = downloader.load(uri, 0).getInputStream();
                this.decoder = BitmapRegionDecoder.newInstance(inputStream, false);
            }

            return new Point(this.decoder.getWidth(), this.decoder.getHeight());
        }

        @Override
        public Bitmap decodeRegion(Rect rect, int sampleSize) {
            synchronized (DECODER_LOCK) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = Bitmap.Config.RGB_565;

                Bitmap bitmap = decodeRegion(rect, options);
                if (bitmap == null) {
                    throw new RuntimeException("Region decoder returned null bitmap - image format may not be supported");
                } else {
                    logger.info("Decoded region {} with {}, resulting image is {}x{}",
                            rect, sampleSize, bitmap.getWidth(), bitmap.getHeight());

                    return bitmap;
                }
            }
        }

        private Bitmap decodeRegion(Rect rect, BitmapFactory.Options options) {
            Bitmap bitmap;
            try {
                bitmap = this.decoder.decodeRegion(rect, options);
            } catch (OutOfMemoryError oom) {
                throw new RuntimeException("Out of memory");
            }
            return bitmap;
        }

        @Override
        public boolean isReady() {
            return this.decoder != null && !this.decoder.isRecycled();
        }

        @Override
        public void recycle() {
            this.decoder.recycle();
        }
    }
}
