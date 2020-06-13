package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.widget.ProgressBar
import com.pr0gramm.app.R
import com.pr0gramm.app.services.DownloadService
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.find

/**
 * This dialog shows the progress while downloading something.
 */
class ProgressDialogController(context: Context) {
    private val dialog: Dialog = dialog(context) {
        layout(R.layout.progress_update)
        onShow { dialog ->
            val p = dialog.find<ProgressBar>(R.id.progress)
            p.isIndeterminate = true
            p.max = 1000
        }
    }

    fun updateStatus(status: DownloadService.Status) {
        if (!dialog.isShowing) {
            return
        }

        dialog.findViewById<ProgressBar>(R.id.progress)?.let { p ->
            p.isIndeterminate = status.progress < 0
            p.progress = (1000 * status.progress).toInt()
        }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
