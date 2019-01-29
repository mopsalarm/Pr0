package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.R
import com.pr0gramm.app.Settings

/**
 */
class CategoriesActionItemsSlide : ActionItemsSlide("CategoriesActionItemsSlide") {
    override val introBackgroundResource: Int = R.color.pink_primary

    override val introTitle: String = "Kategorien"

    override val introDescription: String
            = "Die App bietet dir mehr als nur 'Top' und 'Neu' - welche Kategorien interessieren dich?"

    override val introActionItems: List<ActionItem> get() {
        val settings = Settings.get()
        return listOf(
                SettingActionItem(settings, "Zufall", "pref_show_category_random"),
                SettingActionItem(settings, "Kontrovers", "pref_show_category_controversial"),
                SettingActionItem(settings, "Stelz (nur pr0mium)", "pref_show_category_premium"))
    }
}
