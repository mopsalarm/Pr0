package com.pr0gramm.app.ui.intro.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;

import java.util.List;

/**
 */
public class CategoriesActionItemsSlide extends ActionItemsSlide {
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(R.color.pink_primary);
    }

    @Override
    protected String getIntroTitle() {
        return "Kategorien";
    }

    @Override
    protected String getIntroDescription() {
        return "Die App bietet dir mehr als nur 'Top' und 'Neu' - welche Kategorien interessieren dich?";
    }

    @Override
    protected List<ActionItem> getIntroActionItems() {
        Settings settings = Settings.of(getContext());

        return ImmutableList.of(
                new SettingActionItem(settings, "Zufall", "pref_show_category_random"),
                new SettingActionItem(settings, "Kontrovers", "pref_show_category_controversial"),
                new SettingActionItem(settings, "Text", "pref_show_category_text"),
                new SettingActionItem(settings, "Stelz (nur pr0mium)", "pref_show_category_premium"));
    }
}
