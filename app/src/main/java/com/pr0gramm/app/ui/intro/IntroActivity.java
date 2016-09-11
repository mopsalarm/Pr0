package com.pr0gramm.app.ui.intro;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;

import com.github.paolorotolo.appintro.AppIntro;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.intro.slides.BetaActionItemsSlide;
import com.pr0gramm.app.ui.intro.slides.BookmarksActionItemsSlide;
import com.pr0gramm.app.ui.intro.slides.CategoriesActionItemsSlide;
import com.pr0gramm.app.ui.intro.slides.SettingsActionItemsSlide;
import com.pr0gramm.app.ui.intro.slides.ThemeActionItemsSlide;

public class IntroActivity extends AppIntro {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addSlide(GenericAppIntroFragment.newInstance(
                "",
                "Die folgenden Seiten werden dir helfen, die pr0gramm App an deine Wünsche anzupassen!",
                R.drawable.ic_arrow,
                ContextCompat.getColor(this, R.color.orange_primary)));

        addSlide(new CategoriesActionItemsSlide());
        addSlide(new BookmarksActionItemsSlide());
        addSlide(new ThemeActionItemsSlide());
        addSlide(new SettingsActionItemsSlide());

        if (Math.random() < 0.25 || BuildConfig.DEBUG) {
            // only show beta for a few people
            addSlide(new BetaActionItemsSlide());
        }

        addSlide(GenericAppIntroFragment.newInstance(
                "Danke",
                "Schön, dass du dich für die App entschieden hast. Probiere unbedingt auch die neuen Features, wie das Favorisieren von Kommentaren und die erweiterte Suche, aus! Wenn irgendetwas schief läuft, sende bitte Feedback",
                R.drawable.ic_favorite_white_48dp,
                ContextCompat.getColor(this, R.color.olive_primary)));

        setGoBackLock(true);
        showSkipButton(false);
        setDoneText(getString(R.string.action_done));

        setFadeAnimation();
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        finish();
    }
}
