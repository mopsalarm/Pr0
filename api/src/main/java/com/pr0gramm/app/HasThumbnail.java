package com.pr0gramm.app;


import javax.annotation.Nullable;

/**
 */
public interface HasThumbnail {
    long id();

    @Nullable
    String thumbnail();
}
