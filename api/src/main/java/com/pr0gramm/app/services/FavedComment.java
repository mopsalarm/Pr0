package com.pr0gramm.app.services;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

/**
 * Transfer object for favoured comments
 */
@Value.Immutable
@Gson.TypeAdapters
public interface FavedComment {
    long getId();

    @Gson.Named("item_id")
    long getItemId();

    String getName();

    String getContent();

    int getUp();

    int getDown();

    int getMark();

    Instant getCreated();

    String getThumb();

    int getFlags();
}
