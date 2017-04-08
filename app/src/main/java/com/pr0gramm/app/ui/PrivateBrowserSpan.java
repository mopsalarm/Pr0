package com.pr0gramm.app.ui;

import android.net.Uri;
import android.text.style.URLSpan;
import android.view.View;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.util.CustomTabsHelper;

/**
 */
public class PrivateBrowserSpan extends URLSpan {
    public PrivateBrowserSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(View widget) {
        String url = getURL();

        Settings settings = Settings.of(widget.getContext());
        boolean useIncognitoBrowser = settings.useIncognitoBrowser();

        // check if youtube-links should be opened in normal app
        if (useIncognitoBrowser && settings.overrideYouTubeLinks()) {
            String host = Uri.parse(url).getHost();
            if (host != null && BLACKLIST.contains(host.toLowerCase()))
                useIncognitoBrowser = false;
        }

        if (useIncognitoBrowser) {
            CustomTabsHelper.newWebviewBuilder(widget.getContext()).show(url);

        } else {
            // dispatch link normally
            super.onClick(widget);
        }
    }

    private static final ImmutableList<String> BLACKLIST = ImmutableList.of(
            "youtube.com", "youtu.be", "www.youtube.com", "m.youtube.com",
            "amazon.com", "amazon.de", "amzn.com", "amzn.de",
            "vimeo.com");
}
