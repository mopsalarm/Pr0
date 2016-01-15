package com.pr0gramm.app.services;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.support.v7.view.ContextThemeWrapper;

import com.google.common.base.Enums;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.Themes;

import javax.annotation.Nonnull;

/**
 * A little service to get theme stuff.
 */
public final class ThemeHelper {
    @Nullable
    private static Themes THEME = Themes.ORANGE;

    private ThemeHelper() {
    }

    @ColorRes
    public static int primaryColor() {
        return theme().primaryColor;
    }

    @ColorRes
    public static int primaryColorDark() {
        return theme().primaryColorDark;
    }

    @Nonnull
    public static Themes theme() {
        return THEME != null ? THEME : Themes.ORANGE;
    }


    public static void updateTheme(Context context) {
        Settings settings = Settings.of(context);
        String name = settings.themeName();
        THEME = Enums.getIfPresent(Themes.class, name).or(Themes.ORANGE);
    }

    public static Context popupContext(Context context) {
        return new ContextThemeWrapper(context, theme().popup);
    }
}
