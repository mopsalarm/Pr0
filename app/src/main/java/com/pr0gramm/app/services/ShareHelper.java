package com.pr0gramm.app.services;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.ShareCompat;

import com.google.common.base.Optional;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedType;

/**
 * This class helps starting "Share with"-chooser for a {@link FeedItem}.
 */
public class ShareHelper {
    private ShareHelper() {}

    public static void searchImage(Activity activity, FeedItem feedItem) {
        String imageUri = UriHelper.of(activity).media(feedItem).toString().replace("https://", "http://");

        Uri uri = Uri.parse("https://www.google.com/searchbyimage").buildUpon()
                .appendQueryParameter("hl", "en")
                .appendQueryParameter("safe", "off")
                .appendQueryParameter("site", "search")
                .appendQueryParameter("image_url", imageUri)
                .build();

        Track.searchImage();
        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    public static void sharePost(Activity activity, FeedItem feedItem) {
        String text = feedItem.getPromotedId() > 0
                ? UriHelper.of(activity).post(FeedType.PROMOTED, feedItem.getId()).toString()
                : UriHelper.of(activity).post(FeedType.NEW, feedItem.getId()).toString();

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text)
                .setChooserTitle(R.string.share_with)
                .startChooser();

        Track.share("post");
    }

    public static void shareDirectLink(Activity activity, FeedItem feedItem) {
        String uri = UriHelper.of(activity).noPreload().media(feedItem).toString();

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(uri)
                .setChooserTitle(R.string.share_with)
                .startChooser();

        Track.share("image_link");
    }

    public static void shareImage(Activity activity, FeedItem feedItem) {
        Optional<String> mimetype = ShareProvider.guessMimetype(activity, feedItem);
        if (mimetype.isPresent()) {
            ShareCompat.IntentBuilder.from(activity)
                    .setType(mimetype.get())
                    .addStream(ShareProvider.getShareUri(activity, feedItem))
                    .setChooserTitle(R.string.share_with)
                    .startChooser();

            Track.share("image");
        }
    }
}
