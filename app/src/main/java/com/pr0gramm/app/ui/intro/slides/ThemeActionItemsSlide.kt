package com.pr0gramm.app.ui.intro.slides

import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.Themes

/**
 */
class ThemeActionItemsSlide : ActionItemsSlide("ThemeActionItemsSlide") {
    override val introTitle: String = "Farbe"

    override val introDescription: String =
            "Bist du Neuschwuchtel oder erinnerst du Dich noch an den Kampf der Farben? Wähle deinen persönlichen Sieger."

    override val introActionItems: List<ActionItem>
        get() = Themes.values().map { ThemeActionItem(it) }

    override val singleChoice: Boolean = true

    override val introBackgroundResource: Int = ThemeHelper.theme.accentColor

    private inner class ThemeActionItem(val theme: Themes) : ActionItem(theme.title(context)) {
        private val settings = Settings.get()

        override fun enabled(): Boolean {
            return settings.themeName == theme.name
        }

        override fun activate() {
            ThemeHelper.updateTheme(theme)
            view?.setBackgroundResource(theme.primaryColor)
        }

        override fun deactivate() {}
    }
}
