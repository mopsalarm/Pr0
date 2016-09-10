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
public class BetaActionItemsSlide extends ActionItemsSlide {
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundResource(R.color.feed_background);
    }

    @Override
    protected String getIntroTitle() {
        return "Updates";
    }

    @Override
    protected String getIntroDescription() {
        return "Über neue Updates wirst Du automatisch informiert. Updates kommen normalerweise alle paar Wochen." +
                " Wenn Du helfen möchtest, aktiviere die Beta Updates." +
                " Beta Updates kommen jedoch viel öfter und enthalten möglicherweise Fehler.";
    }

    @Override
    protected List<ActionItem> getIntroActionItems() {
        Settings settings = Settings.of(getContext());

        return ImmutableList.of(
                new SettingActionItem(settings, "Beta aktivieren", "pref_use_beta_channel"));
    }
}
