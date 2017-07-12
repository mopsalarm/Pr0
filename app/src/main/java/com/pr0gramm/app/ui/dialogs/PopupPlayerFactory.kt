package com.pr0gramm.app.ui.dialogs

import android.app.Activity
import android.app.Dialog
import android.view.Window
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaViews

/**
 * Creates a player as a dialog.
 */
object PopupPlayerFactory {
    @JvmStatic
    fun newInstance(activity: Activity, item: FeedItem): Dialog {
        val config = MediaView.Config.ofFeedItem(activity, item)
        return newInstance(config)
    }

    fun newInstance(config: MediaView.Config): Dialog {
        val mediaView = MediaViews.newInstance(config)

        val dialog = Dialog(config.activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(mediaView)

        dialog.setOnShowListener {
            mediaView.onResume()
            mediaView.playMedia()
        }

        dialog.setOnDismissListener {
            mediaView.stopMedia()
            mediaView.onPause()
        }

        dialog.show()
        return dialog
    }
}
