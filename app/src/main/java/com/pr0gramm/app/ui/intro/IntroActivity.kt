package com.pr0gramm.app.ui.intro

import android.app.Activity
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.paolorotolo.appintro.AppIntro
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.ui.intro.slides.*
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.startActivity

class IntroActivity : AppIntro(), LazyInjectorAware {
    override val injector: PropertyInjector = PropertyInjector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        injector.inject(this)

        // deserialize slides to show
        val slidesToShow = intent.getStringArrayExtra("IntroActivity.steps")
                ?.map { enumValueOf<Slides>(it) }
                ?: defaultSlides

        if (Slides.INITIAL in slidesToShow) {
            addSlide(GenericAppIntroFragment.newInstance(
                    title = "",
                    description = "Die folgenden Seiten werden dir helfen, die pr0gramm App an deine Wünsche anzupassen!",
                    imageDrawable = R.drawable.ic_arrow,
                    bgColor = ContextCompat.getColor(this, R.color.orange_primary)))
        }

        if (Slides.UPDATE in slidesToShow) {
            addSlide(GenericAppIntroFragment.newInstance(
                    title = "",
                    description = "Es gab ein paar Updates in der App. Die folgenden Seiten werden dir helfen, die pr0gramm App an deine Wünsche anzupassen und neue Features zu entdecken!",
                    imageDrawable = R.drawable.ic_arrow,
                    bgColor = ContextCompat.getColor(this, R.color.orange_primary)))
        }

        if (Slides.CATEOGRIES in slidesToShow) {
            addSlide(CategoriesActionItemsSlide())
        }

        if (Slides.BOOKMARKS in slidesToShow) {
            addSlide(BookmarksActionItemsSlide())
        }

        if (Slides.THEMES in slidesToShow) {
            addSlide(ThemeActionItemsSlide())
        }

        if (Slides.SETTINGS in slidesToShow) {
            addSlide(SettingsActionItemsSlide())
        }

        if (Slides.BETA in slidesToShow) {
            if (Math.random() < 0.25 || BuildConfig.DEBUG) {
                // only show beta to a few people
                addSlide(BetaActionItemsSlide())
            }
        }

        addSlide(GenericAppIntroFragment.newInstance(
                title = "Danke",
                description = "Schön, dass du dich für die App entschieden hast. Wenn bei dir etwas nicht funktioniert, zögere nicht die Feedback Funktion innerhalb der App zu verwenden.",
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

    companion object {
        private val defaultSlides = listOf(
                Slides.INITIAL, Slides.CATEOGRIES, Slides.BOOKMARKS,
                Slides.THEMES, Slides.SETTINGS, Slides.BETA)

        fun launch(activity: Activity, steps: List<Slides> = defaultSlides) {
            activity.startActivity<IntroActivity>(RequestCodes.INTRO_ACTIVITY) { intent ->
                intent.putExtra("IntroActivity.steps",
                        steps.map { it.name }.toTypedArray<String>())
            }
        }
    }

    enum class Slides {
        INITIAL,
        UPDATE,
        CATEOGRIES,
        BOOKMARKS,
        THEMES,
        SETTINGS,
        BETA
    }
}
