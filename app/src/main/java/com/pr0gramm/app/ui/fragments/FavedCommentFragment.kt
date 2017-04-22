package com.pr0gramm.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.FavedCommentService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.MessageView
import com.pr0gramm.app.ui.showDialog

/**
 */
class FavedCommentFragment : MessageInboxFragment() {
    private val settings = Settings.get()
    private val favedCommentService: FavedCommentService by instance()

    init {
        setHasOptionsMenu(true)
    }

    override fun newLoaderHelper(): LoaderHelper<List<Api.Message>> {
        return LoaderHelper.of {
            favedCommentService
                    .list(settings.contentType)
                    .map { comments -> comments.map { FavedCommentService.commentToMessage(it) } }
        }
    }

    override fun newMessageAdapter(messages: List<Api.Message>): MessageAdapter {
        val adapter = super.newMessageAdapter(messages)
        adapter.pointsVisibility = MessageView.PointsVisibility.NEVER
        return adapter
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_kfav, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_info) {
            showKFavInfoPopup()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showKFavInfoPopup() {
        showDialog(context) {
            content(R.string.info_kfav_userscript)
            negative(R.string.okay)

            positive(R.string.open_website) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://goo.gl/py7xNW"))
                context.startActivity(intent)
            }
        }
    }
}
