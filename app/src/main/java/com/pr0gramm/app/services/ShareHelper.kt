package com.pr0gramm.app.services

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.llamalab.safs.Files
import com.llamalab.safs.android.AndroidFiles
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.util.BrowserHelper
import proguard.annotation.KeepPublicClassMemberNames
import java.util.*

/**
 * This class helps starting "Share with"-chooser for a [FeedItem].
 */
class ShareService(private val cache: Cache) {
    fun searchImage(context: Context, feedItem: FeedItem) {
        val imageUri = UriHelper
                .of(context).media(feedItem).toString()
                .replace("http://", "https://")

        val uri = Settings.get().imageSearchEngine.searchUri(imageUri) ?: return
        Track.searchImage()
        BrowserHelper.open(context, uri.toString())
    }


    fun sharePost(activity: Activity, feedItem: FeedItem) {
        val text = if (feedItem.promotedId > 0)
            UriHelper.of(activity).post(FeedType.PROMOTED, feedItem.id).toString()
        else
            UriHelper.of(activity).post(FeedType.NEW, feedItem.id).toString()

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text)
                .setChooserTitle(R.string.share_with)
                .startChooser()
    }


    fun shareDirectLink(activity: Activity, feedItem: FeedItem) {
        val uri = UriHelper.NoPreload.media(feedItem).toString()

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(uri)
                .setChooserTitle(R.string.share_with)
                .startChooser()
    }


    fun shareUserProfile(activity: Activity, user: String) {
        val uri = UriHelper.of(activity).user(user).toString()

        ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(uri)
                .setChooserTitle(R.string.share_with)
                .startChooser()
    }


    suspend fun shareImage(activity: Activity, feedItem: FeedItem) {
        val mimetype = guessMimetype(activity, feedItem)

        val toShare = withBackgroundContext {
            cache.get(UriHelper.of(activity).media(feedItem)).use { entry ->

                val temporary = Files
                        .createDirectories(AndroidFiles.getCacheDirectory().resolve("share"))
                        .resolve(DownloadService.filenameOf(feedItem))

                entry.inputStreamAt(0).use { inputStream ->
                    Files.newOutputStream(temporary).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                temporary
            }
        }

        val provider = BuildConfig.APPLICATION_ID + ".FileProvider"
        val shareUri = FileProvider.getUriForFile(activity, provider, toShare.toFile())

        // delete the file on vm exit
        toShare.toFile().deleteOnExit()

        ShareCompat.IntentBuilder.from(activity)
                .setType(mimetype)
                .addStream(shareUri)
                .setChooserTitle(R.string.share_with)
                .startChooser()
    }


    fun copyLink(context: Context, feedItem: FeedItem) {
        val helper = UriHelper.of(context)
        val uri = helper.post(FeedType.NEW, feedItem.id).toString()
        copyToClipboard(context, uri)
    }


    fun copyLink(context: Context, feedItem: FeedItem, comment: Api.Comment) {
        val helper = UriHelper.of(context)
        val uri = helper.post(FeedType.NEW, feedItem.id, comment.id).toString()
        copyToClipboard(context, uri)
    }


    private fun copyToClipboard(context: Context, text: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.setPrimaryClip(ClipData.newPlainText(text, text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }


    private fun guessMimetype(context: Context, item: FeedItem): String {
        return guessMimetype(getMediaUri(context, item))
    }

    private fun getMediaUri(context: Context, item: FeedItem): Uri {
        return UriHelper.of(context).media(item)
    }

    private fun guessMimetype(uri: Uri): String {
        val url = uri.toString()
        if (url.length < 4)
            return "application/binary"

        val types = mapOf(
                ".png" to "image/png",
                ".jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "webm" to "video/webm",
                ".mp4" to "video/mp4",
                ".gif" to "image/gif")

        val extension = url.substring(url.length - 4).toLowerCase(Locale.ROOT)
        return types[extension] ?: "application/binary"
    }


    @KeepPublicClassMemberNames
    enum class ImageSearchEngine {
        NONE {
            override fun searchUri(url: String): Uri? {
                return null
            }
        },

        IMGOPS {
            override fun searchUri(url: String): Uri? {
                return Uri.parse("https://imgops.com").buildUpon()
                        .appendEncodedPath(url)
                        .build()
            }
        };

        abstract fun searchUri(url: String): Uri?
    }
}
