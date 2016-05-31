package com.pr0gramm.app.services;

import android.content.Context;
import android.net.Uri;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.preloading.PreloadManager;


/**
 * A little helper class to work with URLs
 */
public final class UriHelper {
    private final Settings settings;
    private final PreloadManager preloadManager;

    private final NoPreload noPreload = new NoPreload();

    private UriHelper(Context context) {
        this.settings = Settings.of(context);

        preloadManager = Dagger.appComponent(context).preloadManager();
    }

    private Uri.Builder start() {
        return new Uri.Builder()
                .scheme(scheme())
                .authority("pr0gramm.com");
    }

    private String scheme() {
        return settings.useHttps() ? "https" : "http";
    }

    private Uri.Builder start(String subdomain) {
        return new Uri.Builder()
                .scheme(scheme())
                .authority(subdomain + ".pr0gramm.com");
    }

    public Uri thumbnail(HasThumbnail item) {
        return preloadManager.get(item.id())
                .transform(pi -> Uri.fromFile(pi.thumbnail()))
                .or(() -> noPreload.thumbnail(item));
    }

    public Uri media(FeedItem item, boolean hq) {
        if (hq && !Strings.isNullOrEmpty(item.fullsize()))
            return noPreload.media(item, true);

        return preloadManager.get(item.id())
                .transform(pi -> Uri.fromFile(pi.media()))
                .or(() -> noPreload.media(item, false));
    }

    public Uri media(FeedItem item) {
        return media(item, false);
    }

    public Uri base() {
        return start().build();
    }

    public Uri post(FeedType type, long itemId) {
        return start().path(FEED_TYPES.get(type))
                .appendPath(String.valueOf(itemId))
                .build();
    }

    public Uri post(FeedType type, long itemId, long commentId) {
        return start().path(FEED_TYPES.get(type))
                .appendEncodedPath(String.valueOf(itemId) + ":comment" + commentId)
                .build();
    }

    public Uri uploads(String user) {
        return start().path("/user/" + user + "/uploads").build();
    }

    public Uri favorites(String user) {
        return start().path("/user/" + user + "/likes").build();
    }

    public NoPreload noPreload() {
        return noPreload;
    }

    public static UriHelper of(Context context) {
        return new UriHelper(context);
    }

    private static final ImmutableMap<FeedType, String> FEED_TYPES = ImmutableMap.<FeedType, String>builder()
            .put(FeedType.NEW, "new")
            .put(FeedType.PROMOTED, "top")
            .put(FeedType.PREMIUM, "stalk")
            .build();

    public class NoPreload {
        private NoPreload() {
        }

        public Uri media(FeedItem item) {
            return media(item, false);
        }

        private Uri media(FeedItem item, boolean highQuality) {
            return highQuality && !item.isVideo()
                    ? join(start("full"), item.fullsize())
                    : join(start(item.isVideo() ? "vid" : "img"), item.image());
        }

        public Uri thumbnail(HasThumbnail item) {
            return join(start("thumb"), item.thumbnail());
        }
    }

    private Uri join(Uri uri, String path) {
        return join(uri.buildUpon(), path);
    }

    private Uri join(Uri.Builder builder, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return Uri.parse(path);
        }

        if (path.startsWith("//")) {
            Uri.parse(scheme() + ":" + path);
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return builder.appendPath(path).build();
    }
}
