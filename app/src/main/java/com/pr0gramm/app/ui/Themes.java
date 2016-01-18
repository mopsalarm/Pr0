package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;

import com.pr0gramm.app.R;

/**
 * App themes.
 */
public enum Themes {
    ORANGE(R.string.theme_orange,
            R.color.orange_primary,
            R.color.orange_primary_dark,
            R.style.AppTheme_Orange,
            R.style.AppTheme_Orange_NoActionBar,
            R.style.AppTheme_Orange_NoActionBar_Fullscreen,
            R.style.AppTheme_Orange_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Orange_NoActionBar_WhiteAccent
    ),

    GREEN(R.string.theme_green,
            R.color.green_primary,
            R.color.green_primary_dark,
            R.style.AppTheme_Green,
            R.style.AppTheme_Green_NoActionBar,
            R.style.AppTheme_Green_NoActionBar_Fullscreen,
            R.style.AppTheme_Green_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Green_NoActionBar_WhiteAccent
    ),

    OLIVE(R.string.theme_olive,
            R.color.olive_primary,
            R.color.olive_primary_dark,
            R.style.AppTheme_Olive,
            R.style.AppTheme_Olive_NoActionBar,
            R.style.AppTheme_Olive_NoActionBar_Fullscreen,
            R.style.AppTheme_Olive_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Olive_NoActionBar_WhiteAccent
    ),

    BLUE(R.string.theme_blue,
            R.color.blue_primary,
            R.color.blue_primary_dark,
            R.style.AppTheme_Blue,
            R.style.AppTheme_Blue_NoActionBar,
            R.style.AppTheme_Blue_NoActionBar_Fullscreen,
            R.style.AppTheme_Blue_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Blue_NoActionBar_WhiteAccent
    ),
    
    PINK(R.string.theme_pink,
            R.color.pink_primary,
            R.color.pink_primary_dark,
            R.style.AppTheme_Pink,
            R.style.AppTheme_Pink_NoActionBar,
            R.style.AppTheme_Pink_NoActionBar_Fullscreen,
            R.style.AppTheme_Pink_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Pink_NoActionBar_WhiteAccent
    );

    @StringRes
    public final int title;

    @ColorRes
    public final int primaryColor;

    @ColorRes
    public final int primaryColorDark;

    @StyleRes
    public final int basic;

    @StyleRes
    public final int noActionBar;

    @StyleRes
    public final int fullscreen;

    @StyleRes
    public final int translucentStatus;

    @StyleRes
    public final int whiteAccent;

    Themes(@StringRes int title,
           @ColorRes int primaryColor,
           @ColorRes int primaryColorDark,
           @StyleRes int basic,
           @StyleRes int noActionBar,
           @StyleRes int fullscreen,
           @StyleRes int translucentStatus,
           @StyleRes int whiteAccent) {

        this.title = title;
        this.primaryColor = primaryColor;
        this.primaryColorDark = primaryColorDark;
        this.basic = basic;
        this.noActionBar = noActionBar;
        this.fullscreen = fullscreen;
        this.translucentStatus = translucentStatus;
        this.whiteAccent = whiteAccent;
    }

    public String title(Context context) {
        return context.getString(title);
    }
}
