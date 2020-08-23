package com.pr0gramm.app.ui

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.BaseDialogFragment

class VersionNotSupportedDialogFragment : BaseDialogFragment("VersionNotSupportedDialogFragment") {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        return dialog(this) {
            contentWithLinks("Support für diese Version der App ist eingestellt. " +
                    "Um die pr0gramm App weiter benutzen zu können, lade die " +
                    "aktuelle Version von https://app.pr0gramm.com herunter.")

            positive(R.string.okay) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.pr0gramm.com")))
                activity?.finish()
            }
        }
    }
}