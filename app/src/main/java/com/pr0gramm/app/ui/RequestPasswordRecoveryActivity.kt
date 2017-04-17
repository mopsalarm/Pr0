package com.pr0gramm.app.ui

import android.os.Bundle
import android.support.v4.util.PatternsCompat
import android.widget.Button
import android.widget.EditText
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding.widget.textChanges

import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import kotterknife.bindView
import rx.functions.Action0

class RequestPasswordRecoveryActivity : BaseAppCompatActivity() {
    private val email: EditText by bindView(R.id.email)
    private val submit: Button by bindView(R.id.submit)

    private val userService: UserService by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_password_recovery)

        email.textChanges()
                .map { PatternsCompat.EMAIL_ADDRESS.matcher(toString().trim()).matches() }
                .subscribe { submit.isEnabled = it }

        submit.setOnClickListener {
            submitButtonClicked()
        }
    }

    fun submitButtonClicked() {
        val email = this.email.text.toString().trim()
        userService.requestPasswordRecovery(email)
                .compose(bindToLifecycleAsync<Any>().forCompletable())
                .subscribe(Action0 { this.requestCompleted() }, defaultOnError())
    }

    private fun requestCompleted() {
        showDialog(this) {
            content(R.string.request_password_recovery_popup_hint)
            positive(R.string.okay) { finish() }
        }
    }
}
