package com.pr0gramm.app.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.UriHelper;

/**
 * Info about a pixels. Can be given to a post fragment to create
 * the fragment animation on newer versions of android.
 */
public final class PreviewInfo {
    private final long itemId;
    private final int width;
    private final int height;
    private final Uri previewUri;

    @Nullable
    private final Drawable preview;


    public PreviewInfo(long id, Uri thumbnail, int width, int height, @Nullable Drawable drawable) {
        this.itemId = id;
        this.previewUri = thumbnail;
        this.width = width;
        this.height = height;
        this.preview = drawable;
    }

    public long getItemId() {
        return itemId;
    }

    @Nullable
    public Drawable getPreview() {
        return preview;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Uri getPreviewUri() {
        return previewUri;
    }

    public static PreviewInfo of(Context context, FeedItem item) {
        return of(context, item, null);
    }

    public static PreviewInfo of(Context context, FeedItem item, @Nullable Drawable drawable) {
        Uri thumbnail = UriHelper.of(context).thumbnail(item);
        return new PreviewInfo(item.id(), thumbnail, item.width(), item.height(), drawable);
    }
}
