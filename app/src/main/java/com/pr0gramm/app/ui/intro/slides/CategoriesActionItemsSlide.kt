package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.R

/**
 */
class CategoriesActionItemsSlide : ActionItemsSlide("CategoriesActionItemsSlide") {
    override val introBackgroundResource: Int = R.color.pink_primary

    override val introTitle: String = "Kategorien"

    override val introDescription: String = "Die App bietet dir mehr als nur 'Top' und 'Neu' - welche Kategorien interessieren dich?"

    override val introActionItems: List<ActionItem>
        get() {
            return listOf(
                    SettingActionItem("Zufall", "pref_show_category_random"),
                    SettingActionItem("Kontrovers", "pref_show_category_controversial"),
                    SettingActionItem("Stelz (nur pr0mium)", "pref_show_category_premium"))
        }
}
