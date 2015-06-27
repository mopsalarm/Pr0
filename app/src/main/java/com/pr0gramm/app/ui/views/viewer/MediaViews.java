package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;

import com.pr0gramm.app.Settings;

/**
 * This class provides static methods to create a new
 * {@link com.pr0gramm.app.ui.views.viewer.MediaView} for some url.
 */
public class MediaViews {
    private MediaViews() {
    }

    /**
     * Instantiates one of the viewer fragments subclasses depending
     * on the provided url.
     *
     * @param uri The url that should be displayed.
     * @return A new {@link MediaView} instance.
     */
    public static MediaView newInstance(Context context, MediaView.Binder binder, MediaUri uri, Runnable onViewListener) {
        Settings settings = Settings.of(context);

        // handle delay urls first.
        if (uri.hasDelayFlag()) {
            return new DelayedMediaView(context, binder, uri.withDelay(false), onViewListener);
        }

        if(settings.useProxy()) {
            uri = uri.withProxy(true);
        }

        MediaView result;
        if (uri.getMediaType() == MediaUri.MediaType.VIDEO) {
            if (shouldUseSoftwareDecoder(uri, settings)) {
                MediaUri mpeg = MediaUri.of(uri.toString().replace(".webm", ".mpg"));
                result = new MpegMediaView(context, binder, mpeg.withProxy(true), onViewListener);
            } else {
                result = new SimpleVideoMediaView(context, binder, uri, onViewListener);
            }

        } else if (uri.getMediaType() == MediaUri.MediaType.GIF) {
            if (settings.convertGifToWebm()) {
                result = new Gif2VideoMediaView(context, binder, uri, onViewListener);
            } else {
                result = new GifMediaView(context, binder, uri, onViewListener);
            }

        } else {
            result = new ImageMediaView(context, binder, uri, onViewListener);
        }

        return result;
    }

    private static boolean shouldUseSoftwareDecoder(MediaUri uri, Settings settings) {
        return uri.getBaseUri().toString().matches(".*pr0gramm.*\\.webm") && settings.useMpegDecoder();
    }
}
