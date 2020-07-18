package com.pr0gramm.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.core.util.PatternsCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.addTextChangedListener
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotterknife.bindView

class RequestPasswordRecoveryActivity : BaseAppCompatActivity("RequestPasswordRecoveryActivity") {
    private val email: EditText by bindView(R.id.email)
    private val submit: Button by bindView(R.id.submit)

    private val userService: UserService by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_password_recovery)

        email.addTextChangedListener { changedText ->
            val valid = PatternsCompat.EMAIL_ADDRESS.matcher(changedText.trim()).matches()
            submit.isEnabled = valid
        }

        submit.setOnClickListener {
            submitButtonClicked()
        }
    }

    private fun submitButtonClicked() {
        val email = this.email.text.toString().trim()

        launchWhenStarted(busyIndicator = true) {
            withViewDisabled(submit) {
                withContext(NonCancellable + Dispatchers.Default) {
                    userService.requestPasswordRecovery(email)
                }
            }

            requestCompleted()
        }
    }

    private fun requestCompleted() {
        showDialog(this) {
            content(R.string.request_password_recovery_popup_hint)
            positive(R.string.okay) { finish() }
        }
    }
}
