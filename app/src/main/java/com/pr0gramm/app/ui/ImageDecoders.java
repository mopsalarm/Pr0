package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Uninterruptibles;
import com.pr0gramm.app.Settings;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
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

        @Nullable
        private BitmapRegionDecoder nativeDecoder;

        @Nullable
        private BitmapDecoder rapidDecoder;

        @Nullable
        private Settings settings;

        private boolean deleteOnRecycle;
        private File tempFile;

        public PicassoRegionDecoder(Downloader downloader) {
            this.downloader = downloader;
        }

        @SuppressLint("NewApi")
        @Override
        public Point init(Context context, Uri uri) throws Exception {
            this.settings = Settings.of(context);

            if ("file".equals(uri.getScheme())) {
                tempFile = toFile(uri);
            } else {
                tempFile = File.createTempFile("image", ".tmp", context.getCacheDir());
                tempFile.deleteOnExit();
                deleteOnRecycle = true;

                // download to temp file. not nice, but useful :/
                try (InputStream inputStream = downloader.load(uri, 0).getInputStream()) {
                    try (FileOutputStream output = new FileOutputStream(tempFile)) {
                        ByteStreams.copy(inputStream, output);
                    }
                }
            }

            try {
                this.nativeDecoder = BitmapRegionDecoder.newInstance(tempFile.getPath(), false);
                if (!tryToDecode(this.nativeDecoder)) {
                    throw new IOException("Could not decode sample using native decoder");
                }

                assert nativeDecoder != null;
                return new Point(this.nativeDecoder.getWidth(), this.nativeDecoder.getHeight());

            } catch (IOException error) {
                if (error.toString().contains("failed to decode")) {
                    nativeDecoder = null;

                    return doNeverAgainOnJvmCrash("init-decoder", () -> {
                        rapidDecoder = BitmapDecoder.from(Uri.fromFile(tempFile)).useBuiltInDecoder();
                        return new Point(rapidDecoder.sourceWidth(), rapidDecoder.sourceHeight());
                    }).or(() -> {
                        throw new IllegalStateException("Wont try fallback decoder.");
                    });
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
                } else if (rapidDecoder != null) {
                    Callable<Bitmap> task = () -> rapidDecoder.reset()
                            .region(rect)
                            .scale(rect.width() / sampleSize, rect.height() / sampleSize)
                            .decode();

                    return doNeverAgainOnJvmCrash("decode", task).or(() -> {
                        throw new IllegalStateException("Wont try fallback decoder.");
                    });

                } else {
                    throw new IllegalStateException("No image decoder available.");
                }
            } finally {
                DECODER_SEMA.release();
            }
        }

        private Bitmap decodeRegion(Rect rect, BitmapFactory.Options options) {
            assert this.nativeDecoder != null;

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

            if (deleteOnRecycle) {
                if (!tempFile.delete()) {
                    logger.warn("Could not delete temporary image");
                }
            }
        }

        @SuppressLint("CommitPrefEdits")
        private <T> Optional<T> doNeverAgainOnJvmCrash(String key, Callable<T> job) {
            String prefName = "doNeverAgain." + key;
            if (settings != null && settings.raw().getBoolean(prefName, false)) {
                return Optional.absent();
            }

            try {
                if (settings != null) {
                    settings.edit().putBoolean(prefName, true).commit();
                }

                return Optional.fromNullable(job.call());

            } catch (Exception error) {
                throw Throwables.propagate(error);

            } finally {
                if (settings != null) {
                    settings.edit().remove(prefName).commit();
                }
            }
        }
    }

    /**
     * Tries to decode a region using the given region decoder.
     *
     * @param decoder The decoder to try.
     * @return true, if decoding was successful.
     */
    private static boolean tryToDecode(BitmapRegionDecoder decoder) {
        int width = Math.min(8, decoder.getWidth());
        int height = Math.min(8, decoder.getHeight());

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return decoder.decodeRegion(new Rect(0, 0, width, height), options) != null;
    }
}
