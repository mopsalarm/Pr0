package com.pr0gramm.app.util;

import android.support.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * Add affiliate tags links to amazon links. *schekel schekel*.
 * No please, seriously, i dont want to put advertisment on the app, but as you all
 * know, it took me a really long time to write this app and to improve it. This way,
 * i might even profit from it, while you guys lose nothing! Much better than ads, isnt it?
 */
public class AmazonAffiliate {
    private static final String AFFILIATE_TAG = "httpsgithub06-21";
    private static final Pattern RE_AMAZON_LINK = Pattern.compile("https?://[^/]+amazon.(de|com)/[^ !.]+");

    /**
     * Adds affiliate links to the given text.
     */
    public String affiliateLinks(@NonNull String text) {
        if (!text.contains("amazon."))
            return text;

        StringBuffer result = new StringBuffer();
        Matcher matcher = RE_AMAZON_LINK.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            try {
                // try to modify the url. If not possible, just ignore any errors.
                url = modifyAmazonUrl(url);
            } catch (Exception ignored) {
            }

            matcher.appendReplacement(result, url);
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String modifyAmazonUrl(String url) {
        HttpUrl uri = HttpUrl.parse(url);
        return uri.newBuilder()
                .setQueryParameter("tag", AFFILIATE_TAG)
                .build()
                .toString();
    }
}
