package com.pr0gramm.app.services;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.ShareCompat;
import android.widget.Toast;

import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedType;

/**
 * This class helps starting "Share with"-chooser for a {@link FeedItem}.
 */
public class ShareHelper {
    private ShareHelper() {
    }

    public static void searchImage(Activity activity, FeedItem feedItem) {
        String imageUri = UriHelper.Companion.of(activity).media(feedItem).toString().replace("https://", "http://");

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
        String text = feedItem.promotedId() > 0
                ? UriHelper.Companion.of(activity).post(FeedType.PROMOTED, feedItem.id()).toString()
                : UriHelper.Companion.of(activity).post(FeedType.NEW, feedItem.id()).toString();

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text)
                .setChooserTitle(R.string.share_with)
                .startChooser();

        Track.share("post");
    }

    public static void shareDirectLink(Activity activity, FeedItem feedItem) {
        String uri = UriHelper.Companion.of(activity).noPreload().media(feedItem).toString();

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(uri)
                .setChooserTitle(R.string.share_with)
                .startChooser();

        Track.share("image_link");
    }

    public static void shareImage(Activity activity, FeedItem feedItem) {
        String mimetype = ShareProvider.Companion.guessMimetype(activity, feedItem);
        ShareCompat.IntentBuilder.from(activity)
                .setType(mimetype)
                .addStream(ShareProvider.Companion.getShareUri(activity, feedItem))
                .setChooserTitle(R.string.share_with)
                .startChooser();

        Track.share("image");
    }

    public static void copyLink(Context context, FeedItem feedItem) {
        UriHelper helper = UriHelper.Companion.of(context);
        String uri = helper.post(FeedType.NEW, feedItem.id()).toString();
        copyToClipboard(context, uri);
    }

    public static void copyLink(Context context, FeedItem feedItem, Api.Comment comment) {
        UriHelper helper = UriHelper.Companion.of(context);
        String uri = helper.post(FeedType.NEW, feedItem.id(), comment.getId()).toString();
        copyToClipboard(context, uri);
    }

    private static void copyToClipboard(Context context, String text) {
        ClipboardManager clipboardManager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);

        clipboardManager.setPrimaryClip(ClipData.newPlainText(text, text));
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }
}
