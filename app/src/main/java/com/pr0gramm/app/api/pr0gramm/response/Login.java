package com.pr0gramm.app.api.pr0gramm.response;

import android.support.annotation.Nullable;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

/**
 */
@Value.Immutable
@Value.Style(get = {"is*", "get*"})
@Gson.TypeAdapters
public interface Login {
    boolean isSuccess();

    @Gson.Named("ban")
    @Nullable
    BanInfo getBanInfo();

    @Value.Immutable
    @Value.Style(get = {"is*", "get*"})
    interface BanInfo {
        boolean isBanned();

        Instant getTill();

        String getReason();
    }
}
