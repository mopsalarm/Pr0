package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;

import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.SimpleProxyService;

import roboguice.RoboGuice;
import roboguice.inject.RoboInjector;

/**
 * This class provides static methods to create a new
 * {@link com.pr0gramm.app.ui.views.viewer.MediaView} for some url.
 */
public class MediaViews {
    private MediaViews() {}

    /**
     * Instantiates one of the viewer fragments subclasses depending
     * on the provided url.
     *
     * @param url The url that should be displayed.
     * @return A new {@link MediaView} instance.
     */
    public static MediaView newInstance(Context context, MediaView.Binder binder, String url) {
        MediaView result;
        Settings settings = Settings.of(context);
        if (isVideoUrl(url)) {
            RoboInjector injector = RoboGuice.getInjector(context);
            SimpleProxyService proxy = injector.getInstance(SimpleProxyService.class);

            // redirect video request though proxy
            url = proxy.getProxyUrl(url);
            result = new VideoMediaView(context, binder, url);

        } else if (url.toLowerCase().endsWith(".gif")) {
            if (settings.convertGifToWebm()) {
                result = new Gif2VideoMediaView(context, binder, url);
            } else {
                result = new GifMediaView(context, binder, url);
            }

        } else {
            result = new ImageMediaView(context, binder, url);
        }

        return result;
    }

    private static boolean isVideoUrl(String url) {
        return url.toLowerCase().matches(".*\\.(webm|mp4|mpg|mpeg|avi)");
    }

    /**
     * Creates a new {@link com.pr0gramm.app.ui.views.viewer.MediaView} instance
     * for the given feed item.
     *
     * @param context  The current context
     * @param feedItem The feed item that is to be displayed.
     * @return A new {@link com.pr0gramm.app.ui.views.viewer.MediaView} instance.
     */
    public static MediaView newInstance(Context context, MediaView.Binder binder, FeedItem feedItem) {
        String url = "http://img.pr0gramm.com/" + feedItem.getImage();
        return newInstance(context, binder, url);
    }
}
