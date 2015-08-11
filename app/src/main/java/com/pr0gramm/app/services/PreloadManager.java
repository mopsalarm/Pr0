package com.pr0gramm.app.services;

import com.google.common.base.Optional;

import org.immutables.value.Value;
import org.joda.time.Instant;

import java.io.File;

/**
 */
public interface PreloadManager {
    void store(PreloadItem entry);

    boolean exists(long itemId);

    Optional<PreloadItem> get(long itemId);

    /**
     */
    @Value.Immutable
    interface PreloadItem {
        long itemId();

        Instant creation();

        File media();

        File thumbnail();
    }
}
