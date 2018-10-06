package com.pr0gramm.app.ui

import android.net.Uri
import android.os.Bundle
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Update
import com.pr0gramm.app.services.UpdateChecker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.util.BrowserHelper

/**
 * This activity is just there to host the update dialog fragment.
 */
class UpdateActivity : BaseAppCompatActivity("UpdateActivity"), DialogDismissListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        val update = intent.getParcelableExtra<Update?>(EXTRA_UPDATE)
        if (savedInstanceState == null && update != null) {
            UpdateChecker.download(this, update)

        } else {
            // forward to app page.
            val uri = Uri.parse("https://app.pr0gramm.com")
            BrowserHelper.openCustomTab(this, uri)
            finish()
        }
    }

    override fun onDialogDismissed(dialog: androidx.fragment.app.DialogFragment) {
        finish()
    }

    companion object {
        const val EXTRA_UPDATE = "UpdateActivity__EXTRA_UPDATE"
    }
}
