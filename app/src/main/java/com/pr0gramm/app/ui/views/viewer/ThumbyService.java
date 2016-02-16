package com.pr0gramm.app.ui.views.viewer;

import android.net.Uri;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.pr0gramm.app.BuildConfig;

import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Simple interface to the thumby service.
 */
public class ThumbyService {
    private static final Pattern RE_VALID_URI = Pattern.compile("^https?://pr0gramm.com/.*");

    private ThumbyService() {
    }

    public static Uri thumbUri(MediaUri mediaUri) {
        if (BuildConfig.DEBUG) {
            checkArgument(isEligibleForPreview(mediaUri), "not eligible for thumby preview");
        }

        // normalize url before fetching generated thumbnail
        String url = mediaUri.getBaseUri().toString()
                .replace("https://", "http://")
                .replace(".mpg", ".webm");

        String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
        return Uri.parse("https://pr0.wibbly-wobbly.de/api/thumby/v1/" + encoded + "/thumb.jpg");
    }


    /**
     * Return true, if the thumby service can produce a pixels for this url.
     * This is currently possible for gifs and videos.
     */
    public static boolean isEligibleForPreview(MediaUri url) {
        MediaUri.MediaType type = url.getMediaType();
        return (type == MediaUri.MediaType.VIDEO || type == MediaUri.MediaType.GIF)
                && RE_VALID_URI.matcher(url.getBaseUri().toString()).matches();
    }
}
