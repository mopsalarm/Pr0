package com.pr0gramm.app.services.config;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
public abstract class Config {
    @Value.Default
    public double googleAnalyticsSampleRate() {
        return 0.5;
    }

    @Value.Default
    public boolean extraCategories() {
        return true;
    }
}
