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
public interface Info {
    User getUser();

    int getLikeCount();

    int getUploadCount();

    int getCommentCount();

    int getTagCount();

    List<UserComments.Comment> getComments();

    @Value.Immutable
    interface User {
        int getId();

        int getMark();

        int getScore();

        String getName();

        Instant getRegistered();
    }
}
