package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.AdminActionDialogBinding
import com.pr0gramm.app.databinding.AdminBanUserBinding
import com.pr0gramm.app.databinding.AdminDeleteCommentBinding
import com.pr0gramm.app.databinding.AdminDeleteItemBinding
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.getParcelableOrNull
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.layoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class ItemUserAdminDialog : ViewBindingDialogFragment<AdminActionDialogBinding>("ItemUserAdminDialog", AdminActionDialogBinding::inflate) {
    private val adminService: AdminService by instance()
    private val configService: ConfigService by instance()

    // extra views
    private var blockUser: CheckBox? = null
    private var blockUserForDays: EditText? = null
    private var blockMode: Spinner? = null
    private var deleteSoft: CheckBox? = null

    private val reasons: List<String> by lazy { configService.config().adminReasons }

    // one of those must be set.
    private val user: String? by lazy { arguments?.getString(KEY_USER) }
    private val item: FeedItem? by lazy { arguments?.getParcelableOrNull(KEY_FEED_ITEM) }
    private val comment: Long? by lazy { arguments?.getLong(KEY_COMMENT)?.takeIf { it > 0 } }

    override fun onCreateDialog(contentView: View): Dialog {
        when {
            comment != null -> {
                val view = AdminDeleteCommentBinding.inflate(contentView.layoutInflater, contentView as ViewGroup)
                blockUser = view.blockUser
                blockUserForDays = view.blockUserDays
                blockMode = view.blockMode
                deleteSoft = view.softDelete
            }

            user != null -> {
                val view = AdminBanUserBinding.inflate(contentView.layoutInflater, contentView as ViewGroup)
                blockUser = view.blockUser
                blockUserForDays = view.blockUserDays
                blockMode = view.blockMode
            }

            item != null -> {
                val view = AdminDeleteItemBinding.inflate(contentView.layoutInflater, contentView as ViewGroup)
                blockUser = view.blockUser
                blockUserForDays = view.blockUserDays
                blockMode = view.blockMode
            }
        }

        return dialog(requireContext()) {
            contentView(contentView)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.okay) { onConfirmClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        val dialog = requireDialog()

        views.reasons.adapter = ArrayAdapter(
                dialog.context, android.R.layout.simple_list_item_1, reasons)

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

        views.reasons.setOnItemClickListener { _, _, position, _ ->
            views.customReason.setText(reasons[position])
        }
    }

    private fun onConfirmClicked() {
        val reason = views.customReason.text.toString().trim()
        if (reason.isEmpty()) {
            return
        }

        launchWhenStarted(busyIndicator = true) {
            withContext(NonCancellable + Dispatchers.Default) {
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
            putParcelable(KEY_FEED_ITEM, item)
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
