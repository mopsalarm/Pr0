package com.pr0gramm.app.api.pr0gramm.response;

import android.support.annotation.Nullable;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

/**
 * A message received from the pr0gramm api.
 */
@Value.Immutable
@Value.Style(init = "with*")
@Gson.TypeAdapters
public interface Message {
    int getId();

    Instant getCreated();

    int getItemId();

    int getMark();

    String getMessage();

    String getName();

    int getScore();

    int getSenderId();

    @Nullable
    String getThumb();

    static Message of(UserComments.User sender, UserComments.Comment comment) {
        return of(sender.getId(), sender.getName(), sender.getMark(), comment);
    }

    static Message of(Info.User sender, UserComments.Comment comment) {
        return of(sender.getId(), sender.getName(), sender.getMark(), comment);
    }

    static Message of(int senderId, String name, int mark, UserComments.Comment comment) {
        return ImmutableMessage.builder()
                .withId((int) comment.getId())
                .withCreated(comment.getCreated())
                .withScore(comment.getUp() - comment.getDown())
                .withItemId((int) comment.getItemId())
                .withMark(mark)
                .withName(name)
                .withSenderId(senderId)
                .withMessage(comment.getContent())
                .withThumb(comment.getThumb())
                .build();
    }
}
