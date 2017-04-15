package com.pr0gramm.app.services

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.app.ShareCompat
import android.widget.Toast
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType

/**
 * This class helps starting "Share with"-chooser for a [FeedItem].
 */
object ShareHelper {
    @JvmStatic
    fun searchImage(activity: Activity, feedItem: FeedItem) {
        val imageUri = UriHelper.of(activity).media(feedItem).toString().replace("https://", "http://")

        val uri = Uri.parse("https://www.google.com/searchbyimage").buildUpon()
                .appendQueryParameter("hl", "en")
                .appendQueryParameter("safe", "off")
                .appendQueryParameter("site", "search")
                .appendQueryParameter("image_url", imageUri)
                .build()

        Track.searchImage()
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    @JvmStatic
    fun sharePost(activity: Activity, feedItem: FeedItem) {
        val text = if (feedItem.promotedId > 0)
            UriHelper.of(activity).post(FeedType.PROMOTED, feedItem.id()).toString()
        else
            UriHelper.of(activity).post(FeedType.NEW, feedItem.id()).toString()

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text)
                .setChooserTitle(R.string.share_with)
                .startChooser()

        Track.share("post")
    }

    @JvmStatic
    fun shareDirectLink(activity: Activity, feedItem: FeedItem) {
        val uri = UriHelper.of(activity).noPreload().media(feedItem).toString()

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(uri)
                .setChooserTitle(R.string.share_with)
                .startChooser()

        Track.share("image_link")
    }

    @JvmStatic
    fun shareImage(activity: Activity, feedItem: FeedItem) {
        val mimetype = ShareProvider.guessMimetype(activity, feedItem)
        ShareCompat.IntentBuilder.from(activity)
                .setType(mimetype)
                .addStream(ShareProvider.getShareUri(activity, feedItem))
                .setChooserTitle(R.string.share_with)
                .startChooser()

        Track.share("image")
    }

    @JvmStatic
    fun copyLink(context: Context, feedItem: FeedItem) {
        val helper = UriHelper.of(context)
        val uri = helper.post(FeedType.NEW, feedItem.id()).toString()
        copyToClipboard(context, uri)
    }

    @JvmStatic
    fun copyLink(context: Context, feedItem: FeedItem, comment: Api.Comment) {
        val helper = UriHelper.of(context)
        val uri = helper.post(FeedType.NEW, feedItem.id(), comment.id).toString()
        copyToClipboard(context, uri)
    }

    @JvmStatic
    private fun copyToClipboard(context: Context, text: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.primaryClip = ClipData.newPlainText(text, text)
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
