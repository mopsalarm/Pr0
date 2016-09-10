package com.pr0gramm.app.ui.intro.slides;

import com.pr0gramm.app.Settings;

/**
 */
class SettingActionItem extends ActionItem {
    private final Settings settings;
    private final String preference;

    SettingActionItem(Settings settings, String title, String preference) {
        super(title);
        this.settings = settings;
        this.preference = preference;
    }

    @Override
    public boolean enabled() {
        return settings.raw().getBoolean(preference, false);
    }

    @Override
    public void activate() {
        settings.edit().putBoolean(preference, true).apply();
    }

    @Override
    public void deactivate() {
        settings.edit().putBoolean(preference, false).apply();
    }
}
