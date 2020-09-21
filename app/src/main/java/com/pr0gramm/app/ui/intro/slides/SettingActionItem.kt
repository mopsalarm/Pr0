package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.Settings

/**
 * Configures settings.
 */
internal class SettingActionItem(title: String, private val preference: String) : ActionItem(title) {
    override fun enabled(): Boolean {
        return Settings.raw().getBoolean(preference, false)
    }

    override fun activate() {
        Settings.edit {
            putBoolean(preference, true)
        }
    }

    override fun deactivate() {
        Settings.edit {
            putBoolean(preference, false)
        }
    }
}
