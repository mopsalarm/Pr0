package com.pr0gramm.app.api.meta;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
interface Names {
    List<String> names();
}
