package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.AutoCompleteTextView
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.ui.DialogBuilder
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.fragments.BusyDialogFragment
import kotterknife.bindView
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 */
class SearchUserDialog : BaseDialogFragment() {
    private val logger = LoggerFactory.getLogger("SearchUserDialog")

    private val inputView by bindView<AutoCompleteTextView>(R.id.username)

    @Inject
    internal lateinit var userService: UserService

    @Inject
    internal lateinit var suggestionService: UserSuggestionService

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return DialogBuilder.start(context)
                .layout(R.layout.search_user_dialog)
                .positive(R.string.action_search_simple, Runnable { onSearchClicked() })
                .negative(DialogBuilder.OnClickListener(Dialog::dismiss))
                .noAutoDismiss()
                .build()
    }

    override fun onDialogViewCreated() {
        inputView.setAdapter(UsernameAutoCompleteAdapter(suggestionService, themedContext,
                "", android.R.layout.simple_dropdown_item_1line))

    }

    private fun onSearchClicked() {
        val username = inputView.text.toString().trim { it <= ' ' }

        userService.info(username)
                .compose(bindToLifecycleAsync())
                .lift(BusyDialogFragment.busyDialog(this))
                .subscribe({ this.onSearchSuccess(it) }, { this.onSearchFailure() })
    }

    private fun onSearchSuccess(info: Api.Info) {
        logger.info("Found user info: {} {}", info.user.id, info.user.name)

        (parentFragment as? Listener)?.onUserInfo(info)
        dismissAllowingStateLoss()
    }

    private fun onSearchFailure() {
        inputView.error = getString(R.string.user_not_found)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    interface Listener {
        fun onUserInfo(info: Api.Info)
    }
}
