package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;

import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.ProxyService;

import roboguice.RoboGuice;

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
     * @param url The url that should be displayed.
     * @return A new {@link MediaView} instance.
     */
    public static MediaView newInstance(Context context, MediaView.Binder binder, String url, Runnable onViewListener) {
        ProxyService proxyService = RoboGuice.getInjector(context).getInstance(ProxyService.class);

        // handle delay: urls.
        if (url.startsWith("delay:")) {
            String realUrl = url.substring("delay:".length());
            return new DelayedMediaView(context, binder, realUrl, onViewListener);
        }

        MediaView result;
        Settings settings = Settings.of(context);
        if (isVideoUrl(url)) {
            if (url.matches("^.*pr0gramm.*\\.webm$") && settings.useMpegDecoder()) {
                result = new MpegMediaView(context, binder, proxyService.proxy(url.replace(".webm", ".mpg")), onViewListener);
            } else {
                result = new VideoMediaView(context, binder, proxyService.proxy(url), onViewListener);
            }

        } else if (url.toLowerCase().endsWith(".gif")) {
            if (settings.convertGifToWebm()) {
                result = new Gif2VideoMediaView(context, binder, url, onViewListener);
            } else {
                result = new GifMediaView(context, binder, proxyService.proxy(url), onViewListener);
            }

        } else {
            result = new ImageMediaView(context, binder, proxyService.proxy(url), onViewListener);
        }

        return result;
    }

    private static boolean isVideoUrl(String url) {
        return url != null && url.toLowerCase().matches(".*\\.(?:webm|mp4|mpg|mpeg|avi)");
    }

    private static boolean isGifUrl(String url) {
        return url != null && url.toLowerCase().endsWith(".gif");
    }

    public static String delay(String url) {
        if (isVideoUrl(url) || isGifUrl(url))
            return "delay:" + url;

        return url;
    }
}
