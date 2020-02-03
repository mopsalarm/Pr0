package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.widget.TextView
import androidx.annotation.StringRes
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.fragments.BusyDialogHelper.dismiss
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.util.checkMainThread

private object BusyDialogHelper {
    private val logger = Logger("BusyDialog")

    fun show(context: Context, text: String): Dialog {
        return showDialog(context) {
            layout(R.layout.progress_dialog)
            cancelable()

            onShow {
                val view = it.findViewById<TextView>(R.id.text)
                view?.text = text
            }
        }
    }

    fun dismiss(dialog: Dialog) {
        try {
            checkMainThread()
            dialog.dismiss()
        } catch (err: Throwable) {
            logger.warn("Could not dismiss busy dialog:", err)

            if (BuildConfig.DEBUG) {
                throw err
            }
        }
    }
}

suspend fun <T> withBusyDialog(
        context: Context, @StringRes textId: Int = R.string.please_wait, block: suspend () -> T): T {

    checkMainThread()

    val dialog = run {
        val text = context.getString(textId)
        BusyDialogHelper.show(context, text)
    }

    try {
        return block()
    } finally {
        dismiss(dialog)
    }
}
