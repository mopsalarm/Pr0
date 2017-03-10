package com.pr0gramm.app.ui.intro.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.ui.Themes;

import java.util.List;

/**
 */
public class ThemeActionItemsSlide extends ActionItemsSlide {
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(ThemeHelper.theme().accentColor);
    }

    @Override
    protected String getIntroTitle() {
        return "Farbe";
    }

    @Override
    protected String getIntroDescription() {
        return "Bist du Neuschwuchtel oder erinnerst du Dich noch an den Kampf der Farben? Wähle deinen persönlichen Sieger.";
    }

    @Override
    protected List<ActionItem> getIntroActionItems() {
        return FluentIterable.from(Themes.values())
                .transform(ThemeActionItem::new)
                .filter(ActionItem.class)
                .toList();
    }

    @Override
    protected boolean singleChoice() {
        return true;
    }

    private class ThemeActionItem extends ActionItem {
        final Settings settings = Settings.of(getContext());
        final Themes theme;

        ThemeActionItem(Themes theme) {
            super(theme.title(getContext()));
            this.theme = theme;
        }

        @Override
        public boolean enabled() {
            return settings.themeName().equals(theme.name());
        }

        @Override
        public void activate() {
            ThemeHelper.updateTheme(getContext(), theme);

            View view = getView();
            if (view != null) {
                view.setBackgroundResource(theme.primaryColor);
            }
        }

        @Override
        public void deactivate() {
        }
    }
}
