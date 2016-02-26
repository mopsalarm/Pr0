package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.net.Uri;

import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.UriHelper;

/**
 */
public final class MediaUri {
    private final long id;
    private final Uri uri;
    private final MediaType mediaType;
    private final boolean proxy;
    private final boolean delay;

    private MediaUri(long id, Uri uri, MediaType mediaType) {
        this(id, uri, mediaType, false, false);
    }

    private MediaUri(long id, Uri uri, MediaType mediaType, boolean proxy, boolean delay) {
        this.id = id;
        this.uri = uri;
        this.mediaType = mediaType;
        this.proxy = proxy;
        this.delay = delay;
    }

    public boolean hasProxyFlag() {
        return proxy;
    }

    public boolean hasDelayFlag() {
        return delay;
    }

    public Uri getBaseUri() {
        return uri;
    }

    public MediaUri withDelay(boolean value) {
        return new MediaUri(id, uri, mediaType, proxy, value);
    }

    public MediaUri withProxy(boolean value) {
        return new MediaUri(id, uri, mediaType, value, delay);
    }

    public MediaUri withUri(Uri uri, MediaType mediaType) {
        return new MediaUri(id, uri, mediaType, true, delay);
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public long getId() {
        return id;
    }

    public boolean isLocal() {
        return "file".equals(uri.getScheme());
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    public enum MediaType {
        IMAGE, VIDEO, GIF
    }

    /**
     * Returns a media uri and guesses the media type from the uri.
     */
    public static MediaUri of(long id, Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null)
            throw new IllegalArgumentException("uri must have a file component");

        MediaType type = MediaType.IMAGE;
        if (name.toLowerCase().endsWith(".gif"))
            type = MediaType.GIF;

        if (name.toLowerCase().matches(".*\\.(webm|mpe?g|mp4)"))
            type = MediaType.VIDEO;

        return new MediaUri(id, uri, type);
    }

    public static MediaUri of(long id, String uri) {
        return of(id, Uri.parse(uri));
    }

    public static MediaUri of(Context context, FeedItem item) {
        return of(item.id(), UriHelper.of(context).media(item));
    }
}
