package com.pr0gramm.app.services;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.StyleRes;

import com.pr0gramm.app.R;

/**
 * A little service to get theme stuff.
 */
public final class ThemeHelper {
    private ThemeHelper() {
    }

    @ColorRes
    public static int primaryColor(Context context) {
        return R.color.orange_primary;
    }

    @ColorRes
    public static int primaryColorDark(Context context) {
        return R.color.orange_primary_dark;
    }

    @StyleRes
    public static int appTheme(Context context) {
        return R.style.AppTheme;
    }
}
