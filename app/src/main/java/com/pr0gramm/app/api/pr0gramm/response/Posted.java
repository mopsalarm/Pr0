package com.pr0gramm.app.api.pr0gramm.response;

import android.support.annotation.Nullable;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

/**
 */
@Value.Immutable
@Value.Enclosing
@Gson.TypeAdapters
public abstract class Posted {
    @Value.Derived
    @Gson.Ignore
    public long getItemId() {
        //noinspection ConstantConditions
        return getItem() != null
                ? getItem().getId()
                : null;
    }

    @Nullable
    public abstract String getError();

    @Nullable
    public abstract Item getItem();

    @Value.Immutable
    public interface Item {
        long getId();
    }
}
