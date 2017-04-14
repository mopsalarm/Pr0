package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.MainActionHandler
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import javax.inject.Inject


/**
 */
class LogoutDialogFragment : BaseDialogFragment() {
    @Inject
    internal lateinit var userService: UserService

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(activity) {
            content(R.string.are_you_sure_to_logout)
            positive(R.string.logout) { logout() }
        }
    }

    private fun logout() {
        (activity as? MainActionHandler)?.onLogoutClicked()
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }
}
