package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.optionalFragmentArgument
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class ReportDialog : BaseDialogFragment("ReportDialog") {
    private val contactService: ContactService by instance()
    private val config: Config by instance()

    private val reasonListView: ListView by bindView(R.id.reason)

    private var itemId: Long by fragmentArgument()
    private var commentId: Long? by optionalFragmentArgument(0)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(requireContext()) {
            layout(R.layout.report_item)
            negative(R.string.cancel) { dismiss() }
            positive(R.string.okay) { onConfirmClicked() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        reasonListView.adapter = ArrayAdapter(dialog.context,
                android.R.layout.simple_list_item_single_choice,
                config.reportReasons)

        val dialog = this.dialog as? AlertDialog
        dialog?.let {
            val button = dialog.getButton(Dialog.BUTTON_POSITIVE)

            button?.isEnabled = false

            reasonListView.setOnItemClickListener { parent, view, position, id ->
                button?.isEnabled = true
            }
        }
    }

    private fun onConfirmClicked() {
        val reason = config.reportReasons.getOrNull(reasonListView.checkedItemPosition) ?: return

        launchWithErrorHandler {
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
