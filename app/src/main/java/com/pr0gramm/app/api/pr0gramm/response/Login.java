package com.pr0gramm.app.api.pr0gramm.response;

import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
public interface Login {
    boolean success();

    @SerializedName("ban")
    @Nullable
    BanInfo banInfo();

    @Value.Immutable
    interface BanInfo {
        boolean banned();

        Instant till();

        String reason();
    }
}
