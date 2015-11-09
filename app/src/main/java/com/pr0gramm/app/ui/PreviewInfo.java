package com.pr0gramm.app.ui;

import android.graphics.drawable.Drawable;
import android.net.Uri;

/**
 * Info about a preview. Can be given to a post fragment to create
 * the fragment animation on newer versions of android.
 */
public final class PreviewInfo {
    private final long itemId;
    private final int width;
    private final int height;
    private Drawable preview;
    private Uri previewUri;


    public PreviewInfo(long itemId, int width, int height) {
        this.itemId = itemId;
        this.width = width;
        this.height = height;
    }

    public PreviewInfo(long itemId, Drawable preview, int width, int height) {
        this(itemId, width, height);
        this.preview = preview;
    }

    public PreviewInfo(long itemId, Uri previewUri, int width, int height) {
        this(itemId, width, height);
        this.previewUri = previewUri;
    }

    public long getItemId() {
        return itemId;
    }

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
}
