package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindOptionalView
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.NonCancellable

/**
 */
class ItemUserAdminDialog : BaseDialogFragment("ItemUserAdminDialog") {
    private val adminService: AdminService by instance()

    private val reasonListView: ListView by bindView(R.id.reason)
    private val customReasonText: EditText by bindView(R.id.custom_reason)

    private val blockUser: CheckBox? by bindOptionalView(R.id.block_user)
    private val blockUserForDays: EditText? by bindOptionalView(R.id.block_user_days)
    private val blockTreeup: CheckBox? by bindOptionalView(R.id.block_treeup)

    private val deleteSoft: CheckBox? by bindOptionalView(R.id.soft_delete)

    // one of those must be set.
    private val user: String? by lazy { arguments?.getString(KEY_USER) }
    private val item: FeedItem? by lazy { arguments?.getFreezable(KEY_FEED_ITEM, FeedItem) }
    private val comment: Long? by lazy { arguments?.getLong(KEY_COMMENT)?.takeIf { it > 0 } }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = when {
            comment != null -> R.layout.admin_delete_comment
            user != null -> R.layout.admin_ban_user
            item != null -> R.layout.admin_delete_item
            else -> throw IllegalArgumentException()
        }

        return dialog(requireContext()) {
            layout(layout)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.okay) { onConfirmClicked() }
            noAutoDismiss()
        }
    }

    override suspend fun onDialogViewCreated() {
        reasonListView.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_1, REASONS)

        blockUser?.let { blockUser ->
            blockTreeup?.isEnabled = blockUser.isChecked

            blockUser.setOnCheckedChangeListener { _, isChecked ->
                blockTreeup?.isEnabled = isChecked
            }
        }

        reasonListView.setOnItemClickListener { _, _, position, _ ->
            customReasonText.setText(REASONS[position])
        }
    }

    private fun onConfirmClicked() {
        val reason = customReasonText.text.toString().trim()
        if (reason.isEmpty()) {
            return
        }

        launchWithErrorHandler(busyIndicator = true) {
            withBackgroundContext(NonCancellable) {
                user?.let { blockUser(it, reason) }
                item?.let { deleteItem(it, reason) }
                comment?.let { deleteComment(it, reason) }
            }

            dismiss()
        }
    }

    private suspend fun deleteItem(item: FeedItem, reason: String) {
        val ban = blockUser?.isChecked ?: false
        val banUserDays = if (ban) blockUserForDays?.text?.toString()?.toFloatOrNull() else null
        adminService.deleteItem(item, reason, banUserDays)
    }

    private suspend fun blockUser(user: String, reason: String) {
        if (blockUser?.isChecked != true)
            return

        val treeup = blockTreeup?.isChecked ?: false
        val banUserDays = blockUserForDays?.text?.toString()?.toFloatOrNull() ?: 0f
        adminService.banUser(user, reason, banUserDays, treeup)
    }

    private suspend fun deleteComment(commentId: Long, reason: String) {
        val deleteHard = !(deleteSoft?.isChecked ?: false)
        adminService.deleteComment(deleteHard, commentId, reason)
    }

    companion object {
        private const val KEY_USER = "userId"
        private const val KEY_FEED_ITEM = "feedItem"
        private const val KEY_COMMENT = "commentId"

        val REASONS = listOf(
                "Repost",
                "Auf Anfrage",
                "Regel #1 - Bild unzureichend getagged (nsfw/nsfl)",
                "Regel #1 - Falsche/Sinnlose Nutzung des NSFP Filters",
                "Regel #2 - Gore/Porn/Suggestive Bilder mit Minderjährigen",
                "Regel #3 - Tierporn/Tierquälerei",
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

        fun forComment(commentId: Long, user: String) = ItemUserAdminDialog().arguments {
            putLong(KEY_COMMENT, commentId)
            putString(KEY_USER, user)
        }
    }
}
