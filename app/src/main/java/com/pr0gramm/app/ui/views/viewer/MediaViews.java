package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;

import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
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
    public static MediaView newInstance(Context context, MediaView.Binder binder, String url) {
        ProxyService proxyService = RoboGuice.getInjector(context).getInstance(ProxyService.class);

        // handle delay: urls.
        if(url.startsWith("delay:")) {
            String realUrl = url.substring("delay:".length());
            return new DelayedMediaView(context, binder, realUrl);
        }

        MediaView result;
        Settings settings = Settings.of(context);
        if (isVideoUrl(url)) {
            // redirect video request though proxy
            result = new VideoMediaView(context, binder, proxyService.proxy(url));

        } else if (url.toLowerCase().endsWith(".gif")) {
            if (settings.convertGifToWebm()) {
                result = new Gif2VideoMediaView(context, binder, url);
            } else {
                result = new GifMediaView(context, binder, proxyService.proxy(url));
            }

        } else {
            result = new ImageMediaView(context, binder, proxyService.proxy(url));
        }

        return result;
    }

    private static boolean isVideoUrl(String url) {
        return url != null && url.toLowerCase().matches(".*\\.(?:webm|mp4|mpg|mpeg|avi)");
    }

    private static boolean isGifUrl(String url) {
        return url != null && url.toLowerCase().endsWith(".gif");
    }

    public static String url(FeedItem feedItem) {
        return "http://img.pr0gramm.com/" + feedItem.getImage();
    }

    public static String delay(String url) {
        if(isVideoUrl(url) || isGifUrl(url))
            return "delay:" + url;

        return url;
    }
}
