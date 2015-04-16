package com.pr0gramm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.common.base.Enums;
import com.google.inject.Singleton;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.ui.fragments.IndicatorStyle;

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

    public boolean analyticsEnabled() {
        return preferences.getBoolean("pref_crashlytics_enabled", true);
    }

    public boolean updateCheckEnabled() {
        return preferences.getBoolean("pref_check_for_updates", true);
    }

    public boolean convertGifToWebm() {
        return preferences.getBoolean("pref_convert_gif_to_webm", false);
    }

    public boolean useCompatVideoPlayer() {
        return preferences.getBoolean("pref_webm_use_compat_viewer", true);
    }

    public int maxImageSize() {
        return Integer.parseInt(preferences.getString("pref_max_image_size", "2048"));
    }

    public IndicatorStyle seenIndicatorStyle() {
        String value = preferences.getString("pref_seen_indicator_style", IndicatorStyle.NONE.toString());
        return Enums.getIfPresent(IndicatorStyle.class, value).or(IndicatorStyle.NONE);
    }

    public boolean animateVoteView() {
        return preferences.getBoolean("pref_animate_vote_view", true);
    }

    public boolean doubleTapToUpvote() {
        return preferences.getBoolean("pref_double_tap_to_upvote", true);
    }

    public boolean smallerVoteViewsOnComments() {
        return preferences.getBoolean("pref_comment_view_vote_buttons_small", false);
    }

    public boolean benisGraphEnabled() {
        return preferences.getBoolean("pref_benis_graph_enabled", true);
    }

    public boolean fullscreenZoomView() {
        return preferences.getBoolean("pref_fullscreen_zoom_view", true);
    }

    public boolean showPinButton() {
        return preferences.getBoolean("pref_show_pin_button", true);
    }

    public boolean showRefreshButton() {
        return preferences.getBoolean("pref_show_refresh_button", true);
    }

    public boolean useBetaChannel() {
        return preferences.getBoolean("pref_use_beta_channel", false);
    }

    public boolean restartWebmOnStop() {
        return preferences.getBoolean("pref_webm_restart_on_stop", false);
    }

    public boolean showNotifications() {
        return preferences.getBoolean("pref_show_notifications", true);
    }

    public static Settings of(Context context) {
        return new Settings(context);
    }

    public static Settings of(SharedPreferences preferences) {
        return new Settings(preferences);
    }
}
