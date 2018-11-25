package com.pr0gramm.app.ui.fragments

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
        return when (item.itemId) {
            R.id.action_new_message -> showNewMessageDialog()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNewMessageDialog(): Boolean {
        val dialog = SearchUserDialog()
        dialog.show(childFragmentManager, null)
        return true
    }

    override suspend fun loadContent(): List<Api.PrivateMessage> {
        return inboxService.privateMessages()
    }

    override fun displayMessages(recyclerView: androidx.recyclerview.widget.RecyclerView, messages: List<Api.PrivateMessage>) {
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
