package com.pr0gramm.app.services.meta;

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
