package com.pr0gramm.app.services

import android.support.annotation.ColorRes
import com.google.common.base.Enums
import com.pr0gramm.app.Settings
import com.pr0gramm.app.ui.Themes

/**
 * A little service to get theme stuff.
 */
object ThemeHelper {
    var theme = Themes.ORANGE
        private set

    val accentColor: Int
        @ColorRes get() = theme.accentColor

    val primaryColorDark: Int
        @ColorRes get() = theme.primaryColorDark

    /**
     * Updates the current theme from settings.
     */
    fun updateTheme() {
        val settings = Settings.get()
        val name = settings.themeName
        theme = Enums.getIfPresent(Themes::class.java, name).or(Themes.ORANGE)
    }

    /**
     * Sets the current theme to the given value and stores it in the settings.
     */
    fun updateTheme(theme: Themes) {
        Settings.get().edit {
            putString("pref_theme", theme.name)
        }

        updateTheme()
    }
}
