package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
public interface Invited {
    @Nullable
    String error();
}
