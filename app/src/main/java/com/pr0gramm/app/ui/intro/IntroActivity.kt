package com.pr0gramm.app.ui.intro

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.github.paolorotolo.appintro.AppIntro
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.ui.intro.slides.BetaActionItemsSlide
import com.pr0gramm.app.ui.intro.slides.CategoriesActionItemsSlide
import com.pr0gramm.app.ui.intro.slides.SettingsActionItemsSlide
import com.pr0gramm.app.ui.intro.slides.ThemeActionItemsSlide
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.startActivity
import com.pr0gramm.app.util.tryEnumValueOf

class IntroActivity : AppIntro(), LazyInjectorAware {
    override val injector: PropertyInjector = PropertyInjector()

    override fun getLayoutId(): Int = R.layout.activity_intro

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        injector.inject(this)

        // deserialize slides to show
        val slidesToShow = intent.getStringArrayExtra("IntroActivity.steps")
                ?.mapNotNull { tryEnumValueOf<Slides>(it) }
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
                description = """
                    Schön, dass du dich für die App entschieden hast.
                    Wenn bei dir etwas nicht funktioniert, zögere nicht die Feedback-Funktion innerhalb der App zu verwenden.
                    Diesen Willkommensbildschirm kannst du in den Einstellungen jederzeit erneut aufrufen.
                    """.trimIndent(),
                imageDrawable = R.drawable.ic_action_favorite,
                bgColor = ContextCompat.getColor(this, R.color.olive_primary)))

        setGoBackLock(true)
        showSkipButton(false)
        setDoneText(getString(R.string.action_done))

        setFadeAnimation()

        find<View>(R.id.bottom).setOnApplyWindowInsetsListener { v, insets ->
            v.updateLayoutParams<RelativeLayout.LayoutParams> {
                bottomMargin = insets.systemWindowInsetBottom
            }

            insets
        }
    }

    override fun onDonePressed(currentFragment: androidx.fragment.app.Fragment?) {
        super.onDonePressed(currentFragment)
        finish()
    }

    companion object {
        private val defaultSlides = listOf(
                Slides.INITIAL, Slides.CATEOGRIES,
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
        THEMES,
        SETTINGS,
        BETA
    }
}
