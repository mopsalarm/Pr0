package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.util.decoupleSubscribe
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.observeOnMain

class DeleteCommentDialog : BaseDialogFragment("DeleteCommentFragment") {
    private val reason: EditText by bindView(R.id.reason)
    private val softCheck: CheckBox by bindView(R.id.soft_delete)

    private var commentId: Long by fragmentArgument()
    private val adminService: AdminService by instance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(activity) {
            noAutoDismiss()
            layout(R.layout.admin_delete_comment)
            positive(R.string.delete) { delete() }
            neutral(R.string.cancel) { it.dismiss() }
        }
    }

    private fun delete() {
        val hard = !softCheck.isChecked
        val reason = this.reason.text.toString()
        adminService.deleteComment(hard, commentId, reason)
                .decoupleSubscribe()
                .observeOnMain()
                .withBusyDialog(this)
                .subscribeWithErrorHandling(childFragmentManager, onComplete = { dismiss() })
    }

    companion object {
        fun newInstance(commentId: Long): DeleteCommentDialog {
            val dialog = DeleteCommentDialog()
            dialog.commentId = commentId
            return dialog
        }
    }
}