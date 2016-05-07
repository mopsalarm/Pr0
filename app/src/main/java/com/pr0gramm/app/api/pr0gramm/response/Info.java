package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

import java.util.List;

import javax.annotation.Nullable;

/**
 */
@Value.Immutable
@Value.Enclosing
@Value.Style(get = {"is*", "get*"})
@Gson.TypeAdapters
public interface Info {
    User getUser();

    int getLikeCount();

    int getUploadCount();

    int getCommentCount();

    int getTagCount();

    boolean likesArePublic();

    boolean following();

    List<UserComments.Comment> getComments();

    @Value.Immutable
    abstract class User {
        public abstract int getId();

        public abstract int getMark();

        public abstract int getScore();

        public abstract String getName();

        public abstract Instant getRegistered();

        @Value.Default
        public int isBanned() {
            return 0;
        }

        @Nullable
        public abstract Instant getBannedUntil();
    }
}
