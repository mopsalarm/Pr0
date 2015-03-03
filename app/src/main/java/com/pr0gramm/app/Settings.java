package com.pr0gramm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.inject.Singleton;

import java.util.EnumSet;

import javax.inject.Inject;

/**
 */
@Singleton
public class Settings {
    private final SharedPreferences preferences;

    @Inject
    public Settings(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private Settings(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public boolean getContentTypeSfw() {
        return preferences.getBoolean("pref_feed_type_sfw", true);
    }

    public boolean getContentTypeNsfw() {
        return preferences.getBoolean("pref_feed_type_nsfw", false);
    }

    public boolean getContentTypeNsfl() {
        return preferences.getBoolean("pref_feed_type_nsfl", false);
    }

    public void registerOnChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterOnChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }


    /**
     * Gets a set of all selected content types.
     */
    public EnumSet<ContentType> getContentType() {
        EnumSet<ContentType> result = EnumSet.allOf(ContentType.class);

        if (!getContentTypeNsfl())
            result.remove(ContentType.NSFL);

        if (!getContentTypeNsfw())
            result.remove(ContentType.NSFW);

        // leave at least sfw.
        if (result.size() > 1 && !getContentTypeSfw())
            result.remove(ContentType.SFW);

        return result;
    }

    public boolean crashlyticsEnabled() {
        return preferences.getBoolean("pref_crashlytics_enabled", true);
    }

    public boolean loadGifInMemory() {
        return preferences.getBoolean("pref_load_gif_in_memory", true);
    }

    public boolean updateCheckEnabled() {
        return preferences.getBoolean("pref_check_for_updates", true);
    }

    public boolean convertGifToWebm() {
        return preferences.getBoolean("pref_convert_gif_to_webm", false);
    }

    public static Settings of(Context context) {
        return new Settings(context);
    }

    public static Settings of(SharedPreferences preferences) {
        return new Settings(preferences);
    }
}
