package com.pr0gramm.app.util.decoders;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.google.common.io.ByteStreams;
import com.squareup.picasso.Downloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkState;
import static com.pr0gramm.app.util.AndroidUtility.toFile;

/**
 * This decoder first downloads the image before starting to decode it.
 */
public class DownloadingRegionDecoder implements ImageRegionDecoder {
    private static final Logger logger = LoggerFactory.getLogger("DownloadingRegionDecoder");

    private final Downloader downloader;
    private final ImageRegionDecoder decoder;

    private File imageFile;
    private boolean deleteImageOnRecycle;

    public DownloadingRegionDecoder(Downloader downloader, ImageRegionDecoder decoder) {
        this.downloader = downloader;
        this.decoder = decoder;
    }

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        checkState(imageFile == null, "Can not call init twice.");

        if ("file".equals(uri.getScheme())) {
            imageFile = toFile(uri);
        } else {
            imageFile = File.createTempFile("image", ".tmp", context.getCacheDir());
            deleteImageOnRecycle = true;

            try {
                downloadTo(downloader, uri, imageFile);

            } catch (IOException error) {
                logger.warn("Could not download image to temp file");

                //noinspection ResultOfMethodCallIgnored
                imageFile.delete();
            }
        }

        return decoder.init(context, Uri.fromFile(imageFile));
    }

    @Override
    public Bitmap decodeRegion(Rect rect, int sample) {
        try {
            Bitmap result = decoder.decodeRegion(rect, sample);
            if (result != null)
                return result;

            throw new RuntimeException("Could not decode");

        } catch (OutOfMemoryError oom) {
            throw new RuntimeException(oom);
        }
    }

    @Override
    public boolean isReady() {
        return imageFile != null && decoder.isReady();
    }

    @Override
    public void recycle() {
        try {
            decoder.recycle();

        } finally {
            if (deleteImageOnRecycle && imageFile.exists()) {
                logger.info("Deleting temp image file on recycle");

                //noinspection ResultOfMethodCallIgnored
                imageFile.delete();
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (deleteImageOnRecycle && imageFile.exists()) {
            logger.warn("Deleting temp image file in finalize.");

            //noinspection ResultOfMethodCallIgnored
            imageFile.delete();
        }
    }

    @SuppressLint("NewApi")
    private static void downloadTo(Downloader downloader, Uri uri, File imageFile) throws IOException {
        // download to temp file. not nice, but useful :/
        try (InputStream inputStream = downloader.load(uri, 0).getInputStream()) {
            try (FileOutputStream output = new FileOutputStream(imageFile)) {
                ByteStreams.copy(inputStream, output);
            }
        }
    }
}
