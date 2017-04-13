package com.pr0gramm.app.services;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;

import com.google.common.base.Enums;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.Themes;

import javax.annotation.Nonnull;

/**
 * A little service to get theme stuff.
 */
public final class ThemeHelper {
    @NonNull
    private static Themes THEME = Themes.ORANGE;

    private ThemeHelper() {
    }

    @ColorRes
    public static int accentColor() {
        return theme().accentColor;
    }

    @ColorRes
    public static int primaryColorDark() {
        return theme().primaryColorDark;
    }

    @Nonnull
    public static Themes theme() {
        return THEME;
    }


    public static void updateTheme(Context context) {
        Settings settings = Settings.get();
        String name = settings.getThemeName();
        THEME = Enums.getIfPresent(Themes.class, name).or(Themes.ORANGE);
    }

    /**
     * Sets the current theme to the given value and stores it in the settings.
     */
    public static void updateTheme(Context context, Themes theme) {
        Settings.get().edit()
                .putString("pref_theme", theme.name())
                .apply();

        updateTheme(context);
    }
}
