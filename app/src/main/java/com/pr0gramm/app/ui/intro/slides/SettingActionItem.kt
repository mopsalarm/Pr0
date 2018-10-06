package com.pr0gramm.app.ui.intro.slides

import androidx.core.content.edit
import com.pr0gramm.app.Settings

/**
 * Configures settings.
 */
internal class SettingActionItem(settings: Settings, title: String, private val preference: String) : ActionItem(title) {
    private val settings = settings.raw()

    override fun enabled(): Boolean {
        return settings.getBoolean(preference, false)
    }

    override fun activate() {
        settings.edit {
            putBoolean(preference, true)
        }
    }

    override fun deactivate() {
        settings.edit {
            putBoolean(preference, false)
        }
    }
}
