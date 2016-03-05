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
import com.google.common.util.concurrent.Uninterruptibles;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import rapid.decoder.BitmapDecoder;

import static com.pr0gramm.app.util.AndroidUtility.toFile;

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

        // We limit the maximum number of decoders in parallel.
        private static final Semaphore DECODER_SEMA = new Semaphore(3);

        private final Downloader downloader;

        private BitmapRegionDecoder nativeDecoder;
        private BitmapDecoder rapidDecoder;

        private boolean deleteOnExit;
        private File tempFile;

        public PicassoRegionDecoder(Downloader downloader) {
            this.downloader = downloader;
        }

        @SuppressLint("NewApi")
        @Override
        public Point init(Context context, Uri uri) throws Exception {
            if ("file".equals(uri.getScheme())) {
                tempFile = toFile(uri);
            } else {
                tempFile = File.createTempFile("image", "tmp", context.getCacheDir());
                tempFile.deleteOnExit();
                deleteOnExit = true;

                // download to temp file. not nice, but useful :/
                try (InputStream inputStream = downloader.load(uri, 0).getInputStream()) {
                    try (FileOutputStream output = new FileOutputStream(tempFile)) {
                        ByteStreams.copy(inputStream, output);
                    }
                }
            }

            try {
                this.nativeDecoder = BitmapRegionDecoder.newInstance(tempFile.getPath(), false);
                return new Point(this.nativeDecoder.getWidth(), this.nativeDecoder.getHeight());

            } catch (IOException error) {
                if (error.toString().contains("failed to decode")) {
                    nativeDecoder = null;

                    rapidDecoder = BitmapDecoder.from(Uri.fromFile(tempFile)).useBuiltInDecoder();
                    return new Point(rapidDecoder.sourceWidth(), rapidDecoder.sourceHeight());
                }

                throw error;
            }
        }

        @Override
        public Bitmap decodeRegion(Rect rect, int sampleSize) {
            Uninterruptibles.tryAcquireUninterruptibly(DECODER_SEMA, 10, TimeUnit.SECONDS);
            try {
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
            } finally {
                DECODER_SEMA.release();
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

            if (deleteOnExit) {
                if (!tempFile.delete()) {
                    logger.warn("Could not delete temporary image");
                }
            }
        }
    }
}
