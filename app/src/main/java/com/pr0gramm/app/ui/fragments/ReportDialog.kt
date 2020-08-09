package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.ReportItemBinding
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.ui.base.ViewBindingDialogFragment
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.optionalFragmentArgument
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class ReportDialog : ViewBindingDialogFragment<ReportItemBinding>("ReportDialog", ReportItemBinding::inflate) {
    private val contactService: ContactService by instance()
    private val config: Config by instance()

    private var itemId: Long by fragmentArgument()
    private var commentId: Long? by optionalFragmentArgument(0)

    override fun onCreateDialog(contentView: View): Dialog {
        return dialog(requireContext()) {
            contentView(contentView)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.okay) { onConfirmClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        val dialog = requireDialog()

        views.reasons.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_single_choice,
                config.reportReasons)

        if (dialog is AlertDialog) {
            val button = dialog.getButton(Dialog.BUTTON_POSITIVE)

            button?.isEnabled = false

            views.reasons.setOnItemClickListener { parent, view, position, id ->
                button?.isEnabled = true
            }
        }
    }

    private fun onConfirmClicked() {
        val reason = config.reportReasons.getOrNull(views.reasons.checkedItemPosition) ?: return

        launchWhenStarted(busyIndicator = true) {
            withContext(NonCancellable) {
                contactService.report(itemId, commentId ?: 0, reason)
            }

            dismiss()
        }
    }

    companion object {
        fun forItem(item: FeedItem) = ReportDialog().apply { this.itemId = item.id }

        fun forComment(item: FeedItem, commentId: Long) = ReportDialog().apply {
            this.itemId = item.id
            this.commentId = commentId
        }
    }
}
