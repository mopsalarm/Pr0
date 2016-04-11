package com.pr0gramm.app.util.decoders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.google.common.base.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;

/**
 * This decoder has a reference to two decoders and initializes and uses the fallback
 * decoder if the firstone encounters an error.
 */
public class FallbackRegionDecoder implements ImageRegionDecoder {
    private static final Logger logger = LoggerFactory.getLogger("FallbackRegionDecoder");
    private final ImageRegionDecoder decoder;

    private Supplier<ImageRegionDecoder> fallbackSupplier;

    @Nullable
    private ImageRegionDecoder fallback;

    public FallbackRegionDecoder(ImageRegionDecoder decoder, Supplier<ImageRegionDecoder> fallbackSupplier) {
        this.decoder = decoder;
        this.fallbackSupplier = fallbackSupplier;
    }

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        try {
            Point result = decoder.init(context, uri);

            // everything is good, lets update our fallback decoder supplier.
            this.fallbackSupplier = makeAfterInitFallbackSupplier(context, uri, fallbackSupplier);
            return result;

        } catch (Exception error) {
            logger.info("Error initializing primary decoder");

            switchToFallback();

            assert fallback != null;
            return fallback.init(context, uri);
        }
    }

    @Override
    public Bitmap decodeRegion(Rect rect, int sampleSize) {
        if (fallback != null) {
            return fallback.decodeRegion(rect, sampleSize);

        } else {
            try {
                // decode normally
                Bitmap result = decoder.decodeRegion(rect, sampleSize);
                if (result != null)
                    return result;

            } catch (RuntimeException error) {
                logger.info("Error in primary decoder.", error);
            }

            // okay, there was an error, lets go to fallback
            switchToFallback();

            assert fallback != null;
            return fallback.decodeRegion(rect, sampleSize);
        }
    }

    @Override
    public boolean isReady() {
        return current().isReady();
    }

    @Override
    public void recycle() {
        current().recycle();
    }

    private ImageRegionDecoder current() {
        return fallback != null ? fallback : decoder;
    }

    private void switchToFallback() {
        try {
            decoder.recycle();
        } catch (Exception ignored) {
        }

        checkState(fallback == null, "Fallback already assigned");
        fallback = fallbackSupplier.get();

        logger.info("Using fallback decoder {}", fallback);
    }

    private static Supplier<ImageRegionDecoder> makeAfterInitFallbackSupplier(Context context, Uri uri, Supplier<ImageRegionDecoder> originalFallbackFactory) {
        return () -> {
            try {
                ImageRegionDecoder fallback = originalFallbackFactory.get();
                fallback.init(context, uri);
                return fallback;

            } catch (Exception error) {
                throw new RuntimeException("Error initializing fallback decoder", error);
            }
        };
    }

    public static ImageRegionDecoder chain(ImageRegionDecoder start, ImageRegionDecoder... fallbacks) {
        if (fallbacks.length == 0) {
            return start;
        } else {
            ImageRegionDecoder[] tail = Arrays.copyOfRange(fallbacks, 1, fallbacks.length);
            return new FallbackRegionDecoder(start, () -> chain(fallbacks[0], tail));
        }
    }
}
