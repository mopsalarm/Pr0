package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.ListView
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding.widget.itemClickEvents
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.util.fragmentArgument
import rx.functions.Action0

/**
 */
class ReportDialog : BaseDialogFragment("ReportDialog") {
    private val contactService: ContactService by instance()
    private val config: Config by instance()

    private val reasonListView: ListView by bindView(R.id.reason)

    private var item: FeedItem by fragmentArgument()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(context) {
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

        (dialog as? AlertDialog)?.let { dialog ->
            val button = dialog.getButton(Dialog.BUTTON_POSITIVE)

            button?.isEnabled = false
            reasonListView.itemClickEvents().subscribe {
                button?.isEnabled = true
            }
        }
    }

    private fun onConfirmClicked() {
        val reason = config.reportReasons.getOrNull(reasonListView.checkedItemPosition) ?: return

        contactService.report(item.id, reason)
                .compose(bindToLifecycleAsync<Any>().forCompletable())
                .withBusyDialog(this)
                .subscribe(Action0 { this.dismiss() }, defaultOnError())

    }

    companion object {
        fun forItem(item: FeedItem) = ReportDialog().apply { this.item = item }
    }
}
