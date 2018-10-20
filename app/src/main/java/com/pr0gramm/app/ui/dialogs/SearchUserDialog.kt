package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.AutoCompleteTextView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.ui.fragments.withBusyDialog
import org.kodein.di.erased.instance

/**
 */
class SearchUserDialog : BaseDialogFragment("SearchUserDialog") {
    private val userService: UserService by instance()
    private val suggestionService: UserSuggestionService by instance()

    private val inputView: AutoCompleteTextView by bindView(R.id.username)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(requireContext()) {
            layout(R.layout.search_user_dialog)
            positive(R.string.action_search_simple) { onSearchClicked() }
            negative { dismiss() }
            noAutoDismiss()
        }
    }

    override fun onDialogViewCreated() {
        inputView.setAdapter(UsernameAutoCompleteAdapter(suggestionService, themedContext,
                android.R.layout.simple_dropdown_item_1line, ""))

    }

    private fun onSearchClicked() {
        val username = inputView.text.toString().trim()

        userService.info(username)
                .bindToLifecycleAsync()
                .withBusyDialog(this)
                .subscribe({ this.onSearchSuccess(it) }, { this.onSearchFailure() })
    }

    private fun onSearchSuccess(info: Api.Info) {
        logger.info { "Found user info: ${info.user.id} ${info.user.name}" }

        (parentFragment as? Listener)?.onUserInfo(info)
        dismissAllowingStateLoss()
    }

    private fun onSearchFailure() {
        inputView.error = getString(R.string.user_not_found)
    }

    interface Listener {
        fun onUserInfo(info: Api.Info)
    }
}
