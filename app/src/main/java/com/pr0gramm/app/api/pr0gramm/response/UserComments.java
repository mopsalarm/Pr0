package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

import java.util.List;

/**
 */
@Value.Immutable
@Value.Enclosing
@Gson.TypeAdapters
public interface UserComments {
    User getUser();

    List<Comment> getComments();

    @Value.Immutable
    interface Comment {
        long getId();

        long getItemId();

        Instant getCreated();

        String getThumb();

        int getUp();

        int getDown();

        String getContent();
    }

    @Value.Immutable
    interface User {
        int getId();

        int getMark();

        String getName();
    }
}
