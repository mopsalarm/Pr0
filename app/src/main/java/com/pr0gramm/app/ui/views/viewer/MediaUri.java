package com.pr0gramm.app.ui.views.viewer;

import android.net.Uri;

/**
 */
public final class MediaUri {
    private final Uri uri;
    private final MediaType mediaType;
    private final boolean proxy;
    private final boolean delay;

    public MediaUri(Uri uri, MediaType mediaType) {
        this(uri, mediaType, false, false);
    }

    private MediaUri(Uri uri, MediaType mediaType, boolean proxy, boolean delay) {
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
        return new MediaUri(uri, mediaType, proxy, value);
    }

    public MediaUri withProxy(boolean value) {
        return new MediaUri(uri, mediaType, value, delay);
    }

    public MediaUri withUri(Uri uri, MediaType mediaType) {
        return new MediaUri(uri, mediaType, true, delay);
    }

    public MediaType getMediaType() {
        return mediaType;
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
    public static MediaUri of(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null)
            throw new IllegalArgumentException("uri must have a file component");

        MediaType type = MediaType.IMAGE;
        if (name.toLowerCase().endsWith(".gif"))
            type = MediaType.GIF;

        if (name.toLowerCase().matches(".*\\.(webm|mpe?g|mp4)"))
            type = MediaType.VIDEO;

        return new MediaUri(uri, type);
    }

    public static MediaUri of(String uri) {
        return of(Uri.parse(uri));
    }
}
