package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.fragmentArgument

/**
 * Creates a player as a dialog.
 */
class PopupPlayer : DialogFragment() {
    var feedItem: FeedItem by fragmentArgument()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val config = MediaView.Config.ofFeedItem(requireActivity(), feedItem)
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

        return dialog
    }

    companion object {
        private const val TAG = "PopupPlayer"

        fun open(activity: FragmentActivity, item: FeedItem) {
            close(activity)

            PopupPlayer().apply {
                feedItem = item
                show(activity.supportFragmentManager, TAG)
            }
        }

        fun close(activity: FragmentActivity) {
            // remove any previously added fragment
            val previous = activity.supportFragmentManager
                    .findFragmentByTag(TAG) as? DialogFragment

            previous?.dismiss()
        }
    }
}



