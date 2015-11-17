package com.pr0gramm.app.services;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

/**
 * Maps to a message definition in json.
 */
@Value.Immutable
@Gson.TypeAdapters
public abstract class MessageDefinition {
    public abstract List<Object> condition();

    public abstract String title();

    public abstract String message();

    @Value.Default
    public boolean notification() {
        return false;
    }
}
