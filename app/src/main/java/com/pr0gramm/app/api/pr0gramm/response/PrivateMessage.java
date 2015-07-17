package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

/**
 */
@Value.Immutable
@Value.Style(get = {"is*", "get*"})
@Gson.TypeAdapters
public interface PrivateMessage {
    int getId();

    Instant getCreated();

    int getRecipientId();

    int getRecipientMark();

    String getRecipientName();

    int getSenderId();

    int getSenderMark();

    String getSenderName();

    boolean isSent();

    String getMessage();
}
