package com.pr0gramm.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.google.code.regexp.Pattern
import com.jakewharton.rxbinding.view.clicks
import com.jakewharton.rxbinding.widget.RxTextView
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.NonCancellable
import kotterknife.bindView

class PasswordRecoveryActivity : BaseAppCompatActivity("PasswordRecoveryActivity") {
    private lateinit var user: String
    private lateinit var token: String

    private val userService: UserService by instance()

    private val submit: Button by bindView(R.id.submit)
    private val password: EditText by bindView(R.id.password)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_recovery)

        val url = intent.getStringExtra("url")
        val matcher = Pattern.compile("/user/(?<user>[^/]+)/resetpass/(?<token>[^/]+)").matcher(url)
        if (matcher.find()) {
            this.user = matcher.group("user")
            this.token = matcher.group("token")
        } else {
            finish()
        }

        RxTextView.textChanges(password)
                .compose(bindToLifecycle<CharSequence>())
                .map { it.toString().trim().length > 6 }
                .subscribe { submit.isEnabled = it }

        submit.clicks().subscribe { submitButtonClicked() }
    }

    private fun submitButtonClicked() {
        val password = this.password.text.toString().trim()

        launchWithErrorHandler(busyIndicator = true) {
            withViewDisabled(submit) {
                val result = withBackgroundContext(NonCancellable) {
                    userService.resetPassword(user, token, password)
                }

                requestCompleted(result)
            }
        }
    }

    private fun requestCompleted(success: Boolean) {
        Track.passwordChanged()

        showDialog(this) {
            content(if (success) R.string.password_recovery_success else R.string.password_recovery_error)
            positive(R.string.okay) { finish() }
        }
    }
}
