package com.pr0gramm.app.services.meta;

import org.immutables.value.Value;

/**
 */
@Value.Immutable
@org.immutables.gson.Gson.TypeAdapters
public interface SizeInfo {
    long getId();

    int getWidth();

    int getHeight();
}
