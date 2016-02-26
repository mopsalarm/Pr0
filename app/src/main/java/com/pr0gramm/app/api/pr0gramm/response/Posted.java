package com.pr0gramm.app.api.pr0gramm.response;

import android.support.annotation.Nullable;

import com.pr0gramm.app.services.HasThumbnail;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

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
                : -1;
    }

    @Nullable
    public abstract String getError();

    @Nullable
    public abstract Item getItem();

    public abstract List<SimilarItem> getSimilar();

    @Value.Immutable
    public interface Item {
        long getId();
    }

    @Value.Immutable
    public interface SimilarItem extends HasThumbnail {
        long id();

        @Gson.Named("thumb")
        String thumbnail();
    }
}
