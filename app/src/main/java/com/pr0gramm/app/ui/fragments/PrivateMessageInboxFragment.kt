package com.pr0gramm.app.ui.fragments

import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.PrivateMessageAdapter
import com.pr0gramm.app.ui.dialogs.SearchUserDialog
import org.kodein.di.erased.instance

/**
 */
class PrivateMessageInboxFragment : InboxFragment<Api.PrivateMessage>("PrivateMessageInboxFragment"), SearchUserDialog.Listener {
    private val userService: UserService by instance()

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_private_messages, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_message -> showNewMessageDialog()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun showNewMessageDialog(): Boolean {
        val dialog = SearchUserDialog()
        dialog.show(childFragmentManager, null)
        return true
    }

    override fun newLoaderHelper(): LoaderHelper<List<Api.PrivateMessage>> {
        return LoaderHelper.of { inboxService.privateMessages }
    }

    override fun displayMessages(recyclerView: RecyclerView, messages: List<Api.PrivateMessage>) {
        val activity = activity ?: return
        recyclerView.adapter = PrivateMessageAdapter(activity, messages, actionListener)
    }

    override fun onUserInfo(info: Api.Info) {
        val user = info.user
        val isSelfInfo = userService.name.equals(user.name, ignoreCase = true)
        if (!isSelfInfo) {
            // only allow sending to other people
            actionListener.onNewPrivateMessage(user.id.toLong(), user.name)
        }
    }

}
