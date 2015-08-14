package com.pr0gramm.app.services;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;

import org.immutables.value.Value;
import org.joda.time.Instant;

import java.io.File;

import rx.Observable;

/**
 */
public interface PreloadManager {
    void store(PreloadItem entry);

    boolean exists(long itemId);

    Optional<PreloadItem> get(long itemId);

    void deleteBefore(Instant threshold);

    Observable<ImmutableCollection<PreloadItem>> all();

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
