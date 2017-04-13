package com.pr0gramm.app.ui

import android.content.Context
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.annotation.StyleRes

import com.pr0gramm.app.R

/**
 * App themes.
 */
enum class Themes(@StringRes val title: Int,
                  @ColorRes val accentColor: Int,
                  @ColorRes val primaryColor: Int,
                  @ColorRes val primaryColorDark: Int,
                  @StyleRes val basic: Int,
                  @StyleRes val noActionBar: Int,
                  @StyleRes val fullscreen: Int,
                  @StyleRes val translucentStatus: Int,
                  @StyleRes val whiteAccent: Int) {

    ORANGE(R.string.theme_orange,
            R.color.orange_accent,
            R.color.orange_primary,
            R.color.orange_primary_dark,
            R.style.AppTheme_Orange,
            R.style.AppTheme_Orange_NoActionBar,
            R.style.AppTheme_Orange_NoActionBar_Fullscreen,
            R.style.AppTheme_Orange_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Orange_NoActionBar_WhiteAccent
    ),

    GREEN(R.string.theme_green,
            R.color.green_accent,
            R.color.green_primary,
            R.color.green_primary_dark,
            R.style.AppTheme_Green,
            R.style.AppTheme_Green_NoActionBar,
            R.style.AppTheme_Green_NoActionBar_Fullscreen,
            R.style.AppTheme_Green_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Green_NoActionBar_WhiteAccent
    ),

    OLIVE(R.string.theme_olive,
            R.color.olive_accent,
            R.color.olive_primary,
            R.color.olive_primary_dark,
            R.style.AppTheme_Olive,
            R.style.AppTheme_Olive_NoActionBar,
            R.style.AppTheme_Olive_NoActionBar_Fullscreen,
            R.style.AppTheme_Olive_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Olive_NoActionBar_WhiteAccent
    ),

    BLUE(R.string.theme_blue,
            R.color.blue_accent,
            R.color.blue_primary,
            R.color.blue_primary_dark,
            R.style.AppTheme_Blue,
            R.style.AppTheme_Blue_NoActionBar,
            R.style.AppTheme_Blue_NoActionBar_Fullscreen,
            R.style.AppTheme_Blue_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Blue_NoActionBar_WhiteAccent
    ),

    PINK(R.string.theme_pink,
            R.color.pink_accent,
            R.color.pink_primary,
            R.color.pink_primary_dark,
            R.style.AppTheme_Pink,
            R.style.AppTheme_Pink_NoActionBar,
            R.style.AppTheme_Pink_NoActionBar_Fullscreen,
            R.style.AppTheme_Pink_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Pink_NoActionBar_WhiteAccent
    ),

    BLACK(R.string.theme_black,
            R.color.black_accent,
            R.color.black_primary,
            R.color.black_primary_dark,
            R.style.AppTheme_Black,
            R.style.AppTheme_Black_NoActionBar,
            R.style.AppTheme_Black_NoActionBar_Fullscreen,
            R.style.AppTheme_Black_NoActionBar_TranslucentStatus,
            R.style.AppTheme_Black_NoActionBar_WhiteAccent
    );

    fun title(context: Context): String {
        return context.getString(title)
    }
}
