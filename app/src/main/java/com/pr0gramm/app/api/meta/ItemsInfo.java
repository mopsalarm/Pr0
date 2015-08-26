package com.pr0gramm.app.api.meta;

import org.immutables.value.Value;

import java.util.List;

/**
 */
@Value.Immutable
@org.immutables.gson.Gson.TypeAdapters
public interface ItemsInfo {
    List<Long> getReposts();

    List<SizeInfo> getSizes();
}
