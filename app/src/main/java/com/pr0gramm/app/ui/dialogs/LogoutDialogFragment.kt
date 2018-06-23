package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.MainActionHandler
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.bottomSheet


/**
 */
class LogoutDialogFragment : BaseDialogFragment("LogoutDialogFragment") {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return bottomSheet(this) {
            content(R.string.are_you_sure_to_logout)
            positive(R.string.logout) { logout() }
            negative { dismiss() }
        }
    }

    private fun logout() {
        (activity as? MainActionHandler)?.onLogoutClicked()
    }
}
