package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.primitives.Bytes;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import rapid.decoder.BitmapDecoder;

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
            try {
                return picasso
                        .load(uri)
                        .tag(tag)
                        .config(Bitmap.Config.RGB_565)
                        .memoryPolicy(MemoryPolicy.NO_CACHE)
                        .get();
            } catch (OutOfMemoryError error) {
                throw new RuntimeException(error);
            }
        }
    }

    public static class PicassoRegionDecoder implements ImageRegionDecoder {
        private static final Logger logger = LoggerFactory.getLogger("PicassoRegionDecoder");

        private static final Object DECODER_LOCK = new Object();
        private final Downloader downloader;

        private BitmapRegionDecoder nativeDecoder;

        private BitmapDecoder rapidDecoder;

        public PicassoRegionDecoder(Downloader downloader) {
            this.downloader = downloader;
        }

        @Override
        public Point init(Context context, Uri uri) throws Exception {
            try {
                return initNativeDecoder(uri);

            } catch (IOException error) {
                if (error.toString().contains("failed to decode")) {
                    nativeDecoder = null;
                    return initRapidDecoder(uri);
                }

                throw error;
            }
        }

        @SuppressLint("NewApi")
        private Point initRapidDecoder(Uri uri) throws IOException {
            if ("file".equals(uri.getScheme())) {
                rapidDecoder = BitmapDecoder.from(uri).useBuiltInDecoder();
                return new Point(rapidDecoder.sourceWidth(), rapidDecoder.sourceHeight());
            } else {
                logger.info("Falling back on 'rapid' decoder");
                byte[] bytes;
                try(InputStream inputStream = downloader.load(uri, 0).getInputStream()) {
                    bytes = ByteStreams.toByteArray(inputStream);
                }

                rapidDecoder = BitmapDecoder.from(bytes).useBuiltInDecoder();
                return new Point(rapidDecoder.sourceWidth(), rapidDecoder.sourceHeight());
            }
        }

        @SuppressLint("NewApi")
        private Point initNativeDecoder(Uri uri) throws IOException {
            if ("file".equals(uri.getScheme())) {
                this.nativeDecoder = BitmapRegionDecoder.newInstance(uri.getPath(), false);
            } else {
                try (InputStream inputStream = downloader.load(uri, 0).getInputStream()) {
                    this.nativeDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
                }
            }

            return new Point(this.nativeDecoder.getWidth(), this.nativeDecoder.getHeight());
        }

        @Override
        public Bitmap decodeRegion(Rect rect, int sampleSize) {
            synchronized (DECODER_LOCK) {
                if (nativeDecoder != null) {
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
                } else {
                    return rapidDecoder.reset()
                            .region(rect)
                            .scale(rect.width() / sampleSize, rect.height() / sampleSize)
                            .decode();
                }
            }
        }

        private Bitmap decodeRegion(Rect rect, BitmapFactory.Options options) {
            Bitmap bitmap;
            try {
                bitmap = this.nativeDecoder.decodeRegion(rect, options);
            } catch (OutOfMemoryError oom) {
                throw new RuntimeException("Out of memory");
            }
            return bitmap;
        }

        @Override
        public boolean isReady() {
            return (this.nativeDecoder != null && !this.nativeDecoder.isRecycled())
                    || this.rapidDecoder != null;
        }

        @Override
        public void recycle() {
            if (nativeDecoder != null) {
                nativeDecoder.recycle();
                nativeDecoder = null;
            }

            if (rapidDecoder != null) {
                rapidDecoder.reset();
                rapidDecoder = null;
            }
        }
    }
}
