package com.pr0gramm.app.services.config;

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.pr0gramm.app.BuildConfig;

import org.immutables.value.Value;

import java.util.Map;

/**
 */
public class Config {
    private final FirebaseRemoteConfig config;

    protected Config(FirebaseRemoteConfig config) {
        this.config = config;
    }

    public boolean extraCategories() {
        return config.getBoolean("extra_categories");
    }

    public long maxUploadSizeNormal() {
        return config.getLong("max_upload_size_normal");
    }

    public long maxUploadSizePremium() {
        return config.getLong("max_upload_size_premium");
    }

    public boolean searchUsingTagService() {
        return config.getBoolean("search_using_tag_service_default");
    }

    public AdType adType() {
        if (BuildConfig.DEBUG) {
            return AdType.FEED;
        }

        return Enums.getIfPresent(AdType.class, config.getString("ad_banner_type")).or(AdType.NONE);
    }

    @Value.Default
    public boolean secretSanta() {
        return config.getBoolean("secret_santa");
    }

    static Map<String, Object> defaultValues() {
        return ImmutableMap.<String, Object>builder()
                .put("extra_categories", true)
                .put("max_upload_size_normal", 6L * 1024 * 1024)
                .put("max_upload_size_premium", 12L * 1024 * 1024)
                .put("search_using_tag_service_default", false)
                .put("secret_santa", false)
                .put("ad_banner_type", "NONE")
                .build();
    }

    public enum AdType {
        NONE, MAIN, FEED
    }
}
