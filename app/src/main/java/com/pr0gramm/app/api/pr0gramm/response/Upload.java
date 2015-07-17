package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

/**
 * Response after uploading a file.
 */
@Value.Immutable
@Gson.TypeAdapters
public interface Upload {
    String getKey();
}
