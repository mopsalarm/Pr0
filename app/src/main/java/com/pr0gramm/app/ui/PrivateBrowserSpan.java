package com.pr0gramm.app.ui;

import android.graphics.Color;
import android.text.style.URLSpan;
import android.view.View;

import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.ThemeHelper;
import com.thefinestartist.finestwebview.FinestWebView;

/**
 */
public class PrivateBrowserSpan extends URLSpan {
    public PrivateBrowserSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(View widget) {
        Settings settings = Settings.of(widget.getContext());
        if (settings.useIncognitoBrowser()) {
            new FinestWebView.Builder(widget.getContext())
                    .theme(ThemeHelper.theme().noActionBar)
                    .iconDefaultColor(Color.WHITE)
                    .toolbarColorRes(ThemeHelper.theme().primaryColor)
                    .progressBarColorRes(ThemeHelper.theme().primaryColorDark)
                    .show(getURL());

        } else {
            // dispatch link normally
            super.onClick(widget);
        }
    }
}
