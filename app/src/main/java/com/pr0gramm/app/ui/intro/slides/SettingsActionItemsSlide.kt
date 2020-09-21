package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.R

/**
 */
class SettingsActionItemsSlide : ActionItemsSlide("SettingsActionItemsSlide") {
    override val introBackgroundResource: Int = R.color.green_primary

    override val introTitle: String = "Einstellungen"

    override val introDescription: String = "Unter Einstellungen gibt es viel zu entdecken. Einfach mal durchblättern!"

    override val introActionItems: List<ActionItem>
        get() {
            return listOf(
                    SettingActionItem("Immer mit 'sfw' starten", "pref_feed_start_at_sfw"),
                    SettingActionItem(getString(R.string.pref_use_incognito_browser_title), "pref_use_incognito_browser"),
                    SettingActionItem(getString(R.string.pref_hide_tag_vote_buttons_title), "pref_hide_tag_vote_buttons"),
                    SettingActionItem(getString(R.string.pref_enable_quick_peek_title), "pref_enable_quick_peek"))
        }
}
