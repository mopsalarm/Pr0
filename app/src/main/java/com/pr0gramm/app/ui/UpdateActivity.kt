package com.pr0gramm.app.ui

import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Update
import com.pr0gramm.app.services.UpdateChecker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.util.CustomTabsHelper
import kotlin.LazyThreadSafetyMode.NONE

/**
 * This activity is just there to host the update dialog fragment.
 */
class UpdateActivity : BaseAppCompatActivity(), DialogDismissListener {

    internal val update: Update? by lazy(NONE) { intent.getParcelableExtra<Update?>(EXTRA_UPDATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme().basic)
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null && this.update != null) {
            UpdateChecker.download(this, update)

        } else {
            // forward to app page.
            val uri = Uri.parse("https://app.pr0gramm.com")
            CustomTabsHelper(this).openCustomTab(uri)
            finish()
        }
    }

    override fun injectComponent(appComponent: ActivityComponent) {
        // nothing to do here
    }

    override fun onDialogDismissed(dialog: DialogFragment) {
        finish()
    }

    companion object {
        val EXTRA_UPDATE = "UpdateActivity__EXTRA_UPDATE"
    }
}
