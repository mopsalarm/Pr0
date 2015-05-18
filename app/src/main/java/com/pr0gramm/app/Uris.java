package com.pr0gramm.app;

import android.content.Context;
import android.net.Uri;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedType;

/**
 * A little helper class to work with URLs
 */
public final class Uris {
    private final Settings settings;

    private Uris(Settings settings) {
        this.settings = settings;
    }

    private Uri.Builder start() {
        return new Uri.Builder()
                .scheme(settings.useHttps() ? "https" : "http")
                .authority("pr0gramm.com");
    }

    private Uri.Builder start(String subdomain) {
        return new Uri.Builder()
                .scheme(settings.useHttps() ? "https" : "http")
                .authority(subdomain + ".pr0gramm.com");
    }

    public Uri thumbnail(FeedItem path) {
        return start("thumb").path(path.getThumb()).build();
    }

    public Uri media(FeedItem item, boolean hq) {
        if (hq && !Strings.isNullOrEmpty(item.getFullsize()))
            return start("full").path(item.getFullsize()).build();

        return start("img").path(item.getImage()).build();
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
                .appendPath(String.valueOf(itemId) + ":comment" + commentId)
                .build();
    }

    public Uri uploads(String user) {
        return start().path("/user/" + user + "/uploads").build();
    }

    public Uri favorites(String user) {
        return start().path("/user/" + user + "/likes").build();
    }

    public static Uris of(Context context) {
        return new Uris(Settings.of(context));
    }

    public static Uris of(Settings settings) {
        return new Uris(settings);
    }

    public static Uris get() {
        return new Uris(Settings.of(Pr0grammApplication.GLOBAL_CONTEXT));
    }

    private static final ImmutableMap<FeedType, String> FEED_TYPES = ImmutableMap.<FeedType, String>builder()
            .put(FeedType.NEW, "new")
            .put(FeedType.PROMOTED, "top")
            .put(FeedType.PREMIUM, "stalk")
            .build();
}
