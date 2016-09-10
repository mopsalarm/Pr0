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
public class SettingsActionItemsSlide extends ActionItemsSlide {
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(R.color.green_primary);
    }

    @Override
    protected String getIntroTitle() {
        return "Einstellungen";
    }

    @Override
    protected String getIntroDescription() {
        return "Unter Einstellungen gibt es viel zu entdecken. Einfach mal durchblättern!";
    }

    @Override
    protected List<ActionItem> getIntroActionItems() {
        Settings settings = Settings.of(getContext());

        return ImmutableList.of(
                new SettingActionItem(settings, "HTTPS verwenden", "pref_use_https"),
                new SettingActionItem(settings, "Immer mit 'sfw' starten", "pref_feed_start_at_sfw"),
                new SettingActionItem(settings, getString(R.string.pref_use_incognito_browser_title), "pref_use_incognito_browser"),
                new SettingActionItem(settings, getString(R.string.pref_double_tap_to_upvote), "pref_double_tap_to_upvote"),
                new SettingActionItem(settings, getString(R.string.pref_hide_tag_vote_buttons_title), "pref_hide_tag_vote_buttons"));
    }
}
