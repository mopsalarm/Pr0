package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import com.github.salomonbrys.kodein.instance
import com.google.common.primitives.Floats
import com.jakewharton.rxbinding.widget.itemClicks
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.util.arguments
import rx.Completable
import rx.functions.Action0

/**
 */
class ItemUserAdminDialog : BaseDialogFragment("ItemUserAdminDialog") {
    private val adminService: AdminService by instance()

    private val reasonListView: ListView by bindView(R.id.reason)
    private val customReasonText: EditText by bindView(R.id.custom_reason)
    private val blockUser: CheckBox by bindView(R.id.block_user)
    private val blockUserForDays: EditText by bindView(R.id.block_user_days)
    private val notifyUser: CheckBox? by bindOptionalView(R.id.notify_user)
    private val blockTreeup: CheckBox? by bindOptionalView(R.id.block_treeup)

    private val item: FeedItem? by lazy { arguments.getParcelable<FeedItem?>(KEY_FEED_ITEM) }
    private val user: String? by lazy { arguments.getString(KEY_USER) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = if (user != null) R.layout.admin_ban_user else R.layout.admin_delete_item

        return dialog(context) {
            layout(layout)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.okay) { onConfirmClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        reasonListView.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_1, AdminService.REASONS)

        if (user != null) {
            blockUser.isChecked = true
            blockUser.isEnabled = false
        }

        reasonListView.itemClicks().subscribe { index ->
            customReasonText.setText(AdminService.REASONS[index])
        }
    }

    private fun onConfirmClicked() {
        val reason = customReasonText.text.toString().trim()
        if (reason.isEmpty()) {
            return
        }

        val completable = null
                ?: item?.let { deleteItem(it, reason) }
                ?: user?.let { blockUser(it, reason) }
                ?: throw IllegalStateException("Either item or user must be set.")

        completable
                .compose(bindToLifecycleAsync<Any>().forCompletable())
                .withBusyDialog(this)
                .subscribe(Action0 { this.dismiss() }, defaultOnError())

    }

    private fun deleteItem(item: FeedItem, reason: String): Completable {
        val notifyUser = this.notifyUser?.isChecked ?: false
        val ban = blockUser.isChecked
        val banUserDays = if (ban) Floats.tryParse(blockUserForDays.text.toString()) else null
        return adminService.deleteItem(item, reason, notifyUser, banUserDays)
    }

    private fun blockUser(user: String, reason: String): Completable {
        val treeup = blockTreeup?.isChecked ?: true
        val banUserDays = Floats.tryParse(blockUserForDays.text.toString()) ?: 0f
        return adminService.banUser(user, reason, banUserDays, treeup)
    }

    companion object {
        private const val KEY_USER = "userId"
        private const val KEY_FEED_ITEM = "feedItem"

        fun forItem(item: FeedItem) = ItemUserAdminDialog().arguments {
            putParcelable(KEY_FEED_ITEM, item)
        }

        fun forUser(name: String) = ItemUserAdminDialog().arguments {
            putString(KEY_USER, name)
        }
    }
}
