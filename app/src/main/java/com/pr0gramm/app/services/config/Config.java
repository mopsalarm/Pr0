package com.pr0gramm.app.services.config;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

/**
 */
@Value.Immutable
@Gson.TypeAdapters
public abstract class Config {
    @Value.Default
    public boolean extraCategories() {
        return true;
    }

    @Value.Default
    public long maxUploadSizeNormal() {
        return 6 * 1024 * 1024;
    }

    @Value.Default
    public long maxUploadSizePremium() {
        return 12 * 1024 * 1024;
    }

    @Value.Default
    public boolean searchUsingTagService() {
        return false;
    }

    @Value.Default
    public boolean secretSanta() {
        return false;
    }

    @Value.Default
    public AdType adType() {
        return AdType.NONE;
    }

    public enum AdType {
        NONE, FEED, MAIN;
    }
}
