package com.pr0gramm.app.api.pr0gramm.response;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

@Gson.TypeAdapters
@Value.Enclosing
@Value.Immutable
public interface AccountInfo {
    Account account();

    @Value.Immutable
    interface Account {
        String email();
    }
}
