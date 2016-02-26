package com.pr0gramm.app.services;

import android.support.annotation.Nullable;

/**
 */
public interface HasThumbnail {
    long id();

    @Nullable
    String thumbnail();
}
