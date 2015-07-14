package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

/**
 */
@SuppressWarnings("unused")
@Value.Immutable
@Gson.TypeAdapters
public interface Comment {
    long getId();

    float getConfidence();

    String getName();

    String getContent();

    Instant getCreated();

    long getParent();

    int getUp();

    int getDown();

    int getMark();
}
