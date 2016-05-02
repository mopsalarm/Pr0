package com.pr0gramm.app.api.pr0gramm.response;

import com.google.common.base.Optional;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

import java.util.List;

@Gson.TypeAdapters
@Value.Enclosing
@Value.Immutable
public interface AccountInfo {
    Account account();

    List<Invite> invited();

    @Value.Immutable
    interface Account {
        String email();

        int invites();
    }

    @Value.Immutable
    interface Invite {
        String email();

        Instant created();

        Optional<String> name();

        Optional<Integer> mark();
    }
}
