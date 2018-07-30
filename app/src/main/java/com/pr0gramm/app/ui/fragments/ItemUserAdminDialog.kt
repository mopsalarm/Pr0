package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import com.jakewharton.rxbinding.widget.checkedChanges
import com.jakewharton.rxbinding.widget.itemClicks
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.util.arguments
import org.kodein.di.erased.instance
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
    private val blockTreeup: CheckBox by bindView(R.id.block_treeup)

    // one of those must be set.
    private val user: String? by lazy { arguments?.getString(KEY_USER) }
    private val item: FeedItem? by lazy { arguments?.getFreezable(KEY_FEED_ITEM, FeedItem) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = if (user != null) R.layout.admin_ban_user else R.layout.admin_delete_item

        return dialog(requireContext()) {
            layout(layout)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.okay) { onConfirmClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        reasonListView.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_1, REASONS)

        if (user != null) {
            blockUser.isChecked = true
            blockUser.isEnabled = false
        }

        blockUser.checkedChanges().subscribe { checked ->
            blockTreeup.isEnabled = checked
        }

        reasonListView.itemClicks().subscribe { index ->
            customReasonText.setText(REASONS[index])
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
        val ban = blockUser.isChecked
        val banUserDays = if (ban) blockUserForDays.text.toString().toFloatOrNull() else null
        return adminService.deleteItem(item, reason, banUserDays)
    }

    private fun blockUser(user: String, reason: String): Completable {
        val treeup = blockTreeup.isChecked
        val banUserDays = blockUserForDays.text.toString().toFloatOrNull() ?: 0f
        return adminService.banUser(user, reason, banUserDays, treeup)
    }

    companion object {
        private const val KEY_USER = "userId"
        private const val KEY_FEED_ITEM = "feedItem"

        val REASONS = listOf(
                "Repost",
                "Auf Anfrage",
                "Regel #1 - Bild unzureichend getagged (nsfw/nsfl)",
                "Regel #1 - Falsche/Sinnlose Nutzung des NSFP Filters",
                "Regel #2 - Gore/Porn/Suggestive Bilder mit Minderjährigen",
                "Regel #3 - Tierporn",
                "Regel #4 - Stumpfer Rassismus/Nazi-Nostalgie",
                "Regel #5 - Werbung/Spam",
                "Regel #6 - Infos zu Privatpersonen",
                "Regel #7 - Bildqualität",
                "Regel #8 - Ähnliche Bilder in Reihe",
                "Regel #11 - Multiaccount",
                "Regel #12 - Warez/Logins zu Pay Sites",
                "Regel #14 - Screamer/Sound-getrolle",
                "Regel #15 - Reiner Musikupload",
                "Regel #16 - Unnötiges Markieren von Mods",
                "Regel #18 - Hetze/Aufruf zur Gewalt",
                "DMCA Anfrage (Copyright)",
                "Müllpost",
                "Trollscheiße.")

        fun forItem(item: FeedItem) = ItemUserAdminDialog().arguments {
            putFreezable(KEY_FEED_ITEM, item)
        }

        fun forUser(name: String) = ItemUserAdminDialog().arguments {
            putString(KEY_USER, name)
        }
    }
}
