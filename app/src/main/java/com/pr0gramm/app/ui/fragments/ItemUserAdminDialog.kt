package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.*
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.base.*
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.NonCancellable

/**
 */
class ItemUserAdminDialog : BaseDialogFragment("ItemUserAdminDialog") {
    private val adminService: AdminService by instance()
    private val configService: ConfigService by instance()

    private val reasonListView: ListView by bindView(R.id.reason)
    private val customReasonText: EditText by bindView(R.id.custom_reason)

    private val blockUser: CheckBox? by bindOptionalView(R.id.block_user)
    private val blockUserForDays: EditText? by bindOptionalView(R.id.block_user_days)
    private val blockMode: Spinner? by bindOptionalView(R.id.block_mode)

    private val deleteSoft: CheckBox? by bindOptionalView(R.id.soft_delete)

    private val reasons: List<String> by lazy { configService.config().adminReasons }

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

    override fun onDialogViewCreated() {
        val dialog = requireDialog()

        reasonListView.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_1, reasons)

        blockUser?.let { blockUser ->
            blockMode?.isEnabled = blockUser.isChecked
            blockUserForDays?.isEnabled = blockUser.isChecked

            blockUser.setOnCheckedChangeListener { _, isChecked ->
                blockMode?.isEnabled = isChecked
                blockUserForDays?.isEnabled = isChecked
            }
        }

        blockMode?.adapter = ArrayAdapter(dialog.context, android.R.layout.simple_spinner_dropdown_item, listOf(
                getString(R.string.hint_block_mode__default),
                getString(R.string.hint_block_mode__single),
                getString(R.string.hint_block_mode__branch)))

        blockMode?.setSelection(0)

        reasonListView.setOnItemClickListener { _, _, position, _ ->
            customReasonText.setText(reasons[position])
        }
    }

    private fun onConfirmClicked() {
        val reason = customReasonText.text.toString().trim()
        if (reason.isEmpty()) {
            return
        }

        launchWhenStarted(busyIndicator = true) {
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

        val modes = listOf(Api.BanMode.Default, Api.BanMode.Single, Api.BanMode.Branch)
        val mode = modes.getOrNull(blockMode?.selectedItemPosition ?: 0) ?: Api.BanMode.Default

        val banUserDays = blockUserForDays?.text?.toString()?.toFloatOrNull() ?: 0f
        adminService.banUser(user, reason, banUserDays, mode)
    }

    private suspend fun deleteComment(commentId: Long, reason: String) {
        val deleteHard = !(deleteSoft?.isChecked ?: false)
        adminService.deleteComment(deleteHard, commentId, reason)
    }

    companion object {
        private const val KEY_USER = "userId"
        private const val KEY_FEED_ITEM = "feedItem"
        private const val KEY_COMMENT = "commentId"

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
