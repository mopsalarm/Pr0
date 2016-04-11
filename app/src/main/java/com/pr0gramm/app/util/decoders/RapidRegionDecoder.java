package com.pr0gramm.app.util.decoders;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.util.AndroidUtility;

import java.util.concurrent.Callable;

import rapid.decoder.BitmapDecoder;

/**
 */
public class RapidRegionDecoder implements ImageRegionDecoder {
    private final Bitmap.Config config;
    private BitmapDecoder decoder;
    private Settings settings;

    public RapidRegionDecoder(Bitmap.Config config) {
        this.config = config;
    }

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        settings = Settings.of(context);

        return doNeverAgainOnJvmCrash("rapid", () -> {
            decoder = BitmapDecoder.from(uri).useBuiltInDecoder().config(config);
            return new Point(decoder.sourceWidth(), decoder.sourceHeight());
        }).or(() -> {
            RuntimeException error = new RuntimeException("Wont try rapid decoder again.");
            AndroidUtility.logToCrashlytics(error);
            throw error;
        });
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public Bitmap decodeRegion(Rect rect, int sampleSize) {
        return doNeverAgainOnJvmCrash("rapid", () -> {
            Bitmap result = decoder.region(rect)
                    .scale(rect.width() / sampleSize, rect.height() / sampleSize)
                    .decode();

            return result;

        }).or(() -> {
            RuntimeException error = new RuntimeException("Wont try rapid decoder again.");
            AndroidUtility.logToCrashlytics(error);
            throw error;
        });
    }

    @Override
    public boolean isReady() {
        return decoder != null;
    }

    @Override
    public void recycle() {
        if (decoder != null) {
            decoder.reset();
            decoder = null;
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
