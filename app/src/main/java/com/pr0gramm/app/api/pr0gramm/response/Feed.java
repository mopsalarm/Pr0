package com.pr0gramm.app.api.pr0gramm.response;

import com.google.common.base.Optional;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

import java.util.List;

/**
 * Feed class maps the json returned for a call to the
 * api endpoint <code>/api/items/get</code>.
 */
@Value.Immutable
@Value.Enclosing
@Value.Style(get = {"is*", "get*"})
@Gson.TypeAdapters
public interface Feed {
    boolean isAtStart();

    boolean isAtEnd();

    List<Item> getItems();

    Optional<String> getError();

    @Value.Immutable
    interface Item {
        long getId();

        long getPromoted();

        String getImage();

        String getThumb();

        String getFullsize();

        String getUser();

        int getUp();

        int getDown();

        int getMark();

        int getFlags();

        Instant getCreated();
    }
}
