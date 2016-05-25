package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
public interface Sync {
    long logLength();

    String log();

    int inboxCount();
}
