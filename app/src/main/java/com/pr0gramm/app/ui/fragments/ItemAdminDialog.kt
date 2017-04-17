package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import com.github.salomonbrys.kodein.instance
import com.google.common.base.MoreObjects.firstNonNull
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
class ItemAdminDialog : BaseDialogFragment() {
    private val adminService: AdminService by instance()

    private val reasonListView: ListView by bindView(R.id.reason)
    private val customReasonText: EditText by bindView(R.id.custom_reason)
    private val blockUser: CheckBox by bindView(R.id.block_user)
    private val blockUserForDays: EditText by bindView(R.id.block_user_days)
    private val notifyUser: CheckBox by bindView(R.id.notify_user)

    private val item by lazy { arguments.getParcelable<FeedItem>(KEY_FEED_ITEM) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(context) {
            layout(R.layout.admin_delete_item)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.delete) { onDeleteClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        reasonListView.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_1, AdminService.REASONS)

        reasonListView.itemClicks().subscribe { index ->
            customReasonText.setText(AdminService.REASONS[index])
        }
    }

    private fun onDeleteClicked() {
        val reason = customReasonText.text.toString().trim()
        val notifyUser = this.notifyUser.isChecked
        val ban = blockUser.isChecked

        if (reason.isEmpty()) {
            return
        }

        deleteItem(reason, notifyUser, ban)
                .compose(bindToLifecycleAsync<Any>().forCompletable())
                .withBusyDialog(this)
                .subscribe(Action0 { this.dismiss() }, defaultOnError())

    }

    private fun deleteItem(reason: String, notifyUser: Boolean, ban: Boolean): Completable {
        if (ban) {
            val banUserDays = firstNonNull(Floats.tryParse(blockUserForDays.text.toString()), 1f)
            return adminService.deleteItem(item, reason, notifyUser, banUserDays)
        } else {
            return adminService.deleteItem(item, reason, notifyUser)
        }
    }

    companion object {
        private const val KEY_FEED_ITEM = "feedItem"

        fun newInstance(item: FeedItem) = ItemAdminDialog().arguments {
            putParcelable(KEY_FEED_ITEM, item)
        }
    }
}
