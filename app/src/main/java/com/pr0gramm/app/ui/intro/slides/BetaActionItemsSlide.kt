package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.R
import com.pr0gramm.app.Settings

/**
 */
class BetaActionItemsSlide : ActionItemsSlide() {
    override val introBackgroundResource = R.color.feed_background

    override val introTitle = "Updates"

    override val introDescription = "Über neue Updates wirst Du automatisch informiert. " +
            "Updates kommen normalerweise alle paar Wochen. Wenn Du helfen möchtest, " +
            "aktiviere die Beta Updates. Beta Updates kommen jedoch viel öfter und " +
            "enthalten möglicherweise Fehler."

    override val introActionItems: List<ActionItem> get() {
        val settings = Settings.of(context)
        return listOf(SettingActionItem(settings, "Beta aktivieren", "pref_use_beta_channel"))
    }
}
