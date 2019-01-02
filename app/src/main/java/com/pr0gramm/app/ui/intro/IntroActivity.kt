package com.pr0gramm.app.ui.intro

import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.paolorotolo.appintro.AppIntro
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.intro.slides.*
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector

class IntroActivity : AppIntro(), LazyInjectorAware {
    override val injector: PropertyInjector = PropertyInjector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        injector.inject(this)

        addSlide(GenericAppIntroFragment.newInstance(
                title = "",
                description = "Die folgenden Seiten werden dir helfen, die pr0gramm App an deine Wünsche anzupassen!",
                imageDrawable = R.drawable.ic_arrow,
                bgColor = ContextCompat.getColor(this, R.color.orange_primary)))

        addSlide(CategoriesActionItemsSlide())
        addSlide(BookmarksActionItemsSlide())
        addSlide(ThemeActionItemsSlide())
        addSlide(SettingsActionItemsSlide())

        if (Math.random() < 0.25 || BuildConfig.DEBUG) {
            // only show beta to a few people
            addSlide(BetaActionItemsSlide())
        }

        addSlide(GenericAppIntroFragment.newInstance(
                title = "Danke",
                description = "Schön, dass du dich für die App entschieden hast. Probiere unbedingt auch die neuen Features, wie das Favorisieren von Kommentaren und die erweiterte Suche, aus! Wenn irgendetwas schief läuft, sende bitte Feedback",
                imageDrawable = R.drawable.ic_favorite_white_48dp,
                bgColor = ContextCompat.getColor(this, R.color.olive_primary)))

        setGoBackLock(true)
        showSkipButton(false)
        setDoneText(getString(R.string.action_done))

        setFadeAnimation()
    }

    override fun onDonePressed(currentFragment: androidx.fragment.app.Fragment?) {
        super.onDonePressed(currentFragment)
        finish()
    }
}
