package com.pr0gramm.app.api.meta;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
public interface UserInfo {
    List<BenisHistoryEntry> getBenisHistory();

    @Value.Immutable(builder = false)
    interface BenisHistoryEntry {
        @Value.Parameter(order = 0)
        long getTimestamp();

        @Value.Parameter(order = 1)
        int getBenis();
    }
}
