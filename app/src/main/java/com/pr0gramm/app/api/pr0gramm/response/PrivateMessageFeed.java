package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

/**
 */
@Value.Immutable
@Value.Style(get = {"is*", "get*"})
@Gson.TypeAdapters
public interface PrivateMessageFeed {
    List<PrivateMessage> getMessages();
}
