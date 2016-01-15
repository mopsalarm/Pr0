package com.pr0gramm.app.services;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;

import com.pr0gramm.app.ui.Themes;

import javax.annotation.Nonnull;

/**
 * A little service to get theme stuff.
 */
public final class ThemeHelper {
    @Nullable
    private static Themes THEME;

    private ThemeHelper() {
    }

    @ColorRes
    public static int primaryColor(Context context) {
        return theme(context).primaryColor;
    }

    @ColorRes
    public static int primaryColorDark(Context context) {
        return theme(context).primaryColorDark;
    }

    @Nonnull
    public static Themes theme(Context context) {
        if (THEME == null) {
            THEME = Themes.ORANGE;
        }

        return THEME;
    }


    public static void invalidate() {
        THEME = null;
    }
}
