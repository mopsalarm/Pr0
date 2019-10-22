package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.decodeBase64
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.ThemeHelper.primaryColorDark
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.ui.views.BusyIndicator
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import kotterknife.bindView

typealias Callback = () -> Unit

/**
 */
class LoginActivity : BaseAppCompatActivity("LoginActivity") {
    private val userService: UserService by instance()
    private val prefs: SharedPreferences by instance()

    private val usernameView: EditText by bindView(R.id.username)
    private val passwordView: EditText by bindView(R.id.password)
    private val submitView: Button by bindView(R.id.login)

    private val captchaBusy: BusyIndicator by bindView(R.id.captcha_busy)
    private val captchaImageView: ImageView by bindView(R.id.captcha_image)
    private val captchaAspect: AspectLayout by bindView(R.id.captcha_aspect)
    private val captchaAnswerView: EditText by bindView(R.id.captcha_answer)

    private var captchaIsLoading: Boolean = false
    private var captchaToken: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.whiteAccent)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // restore last username
        val defaultUsername = prefs.getString(PREF_USERNAME, "")
        if (!defaultUsername.isNullOrEmpty() && "@" !in defaultUsername) {
            usernameView.setText(defaultUsername)
        }

        submitView.setOnClickListener { onLoginClicked() }

        find<View>(R.id.register).setOnClickListener { onRegisterClicked() }
        find<View>(R.id.password_recovery).setOnClickListener { onPasswordRecoveryClicked() }

        updateActivityBackground()

        usernameView.addTextChangedListener { updateSubmitViewEnabled() }
        passwordView.addTextChangedListener { updateSubmitViewEnabled() }
        captchaAnswerView.addTextChangedListener { updateSubmitViewEnabled() }

        captchaAspect.setOnClickListener { updateUserCaptcha() }

        updateSubmitViewEnabled()

        updateUserCaptcha()
    }

    private fun updateUserCaptcha() {
        if (captchaIsLoading) {
            return
        }

        captchaIsLoading = true

        captchaToken = null

        // show busy indicator while loading image
        captchaBusy.isVisible = true
        captchaImageView.isVisible = false

        // clear previous input value
        captchaAnswerView.setText("")
        captchaAnswerView.isEnabled = false

        launchWithErrorHandler {
            try {
                val captcha = userService.userCaptcha()

                // decode the image
                val image = captcha.decodeImage(androidContext)
                val aspect = image.intrinsicWidth.toFloat() / image.intrinsicHeight.toFloat()

                // and set it on the view
                captchaImageView.setImageDrawable(image)
                captchaImageView.isVisible = true

                // also correct the aspect ratio
                captchaAspect.aspect = aspect

                captchaAnswerView.isEnabled = true

                captchaToken = captcha.token
            } finally {
                captchaIsLoading = false
                captchaBusy.isVisible = false
            }
        }
    }

    private fun updateSubmitViewEnabled() {
        val usernameSet = usernameView.text.isNotBlank()
        val passwordSet = passwordView.text.isNotBlank()
        val captchaSet = captchaAnswerView.text.isNotBlank()

        // only accept usernames
        val isMailAddress = "@" in usernameView.text

        if (isMailAddress) {
            usernameView.error = getString(R.string.hint_no_email)
        }

        submitView.isEnabled = usernameSet && passwordSet && captchaSet && !isMailAddress
    }

    private fun updateActivityBackground() {
        val style = ThemeHelper.theme.whiteAccent

        @DrawableRes
        val drawableId = theme.obtainStyledAttributes(style, R.styleable.AppTheme).use {
            it.getResourceId(R.styleable.AppTheme_loginBackground, 0)
        }

        if (drawableId == 0)
            return

        val fallbackColor = ContextCompat.getColor(this, primaryColorDark)
        val background = createBackgroundDrawable(drawableId, fallbackColor)
        ViewCompat.setBackground(findViewById(R.id.content), background)
    }

    private fun createBackgroundDrawable(drawableId: Int, fallbackColor: Int): Drawable {
        return WrapCrashingDrawable(fallbackColor,
                ResourcesCompat.getDrawable(resources, drawableId, theme)!!)
    }

    private fun onLoginClicked() {
        val token = captchaToken ?: return

        val username = usernameView.text.toString()
        val password = passwordView.text.toString()
        val captchaAnswer = captchaAnswerView.text.toString()

        if (username.isEmpty()) {
            usernameView.error = getString(R.string.must_not_be_empty)
            return
        }

        if (password.isEmpty()) {
            passwordView.error = getString(R.string.must_not_be_empty)
            return
        }

        if (captchaAnswer.isEmpty()) {
            captchaAnswerView.error = getString(R.string.must_not_be_empty)
            return
        }

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply()

        Track.loginStarted()

        launchWithErrorHandler(busyIndicator = true) {
            withViewDisabled(usernameView, passwordView, submitView) {
                handleLoginResult(userService.login(username, password, token, captchaAnswer))
            }
        }
    }

    private fun handleLoginResult(response: UserService.LoginResult) {
        when (response) {
            is UserService.LoginResult.Success -> {
                SyncWorker.scheduleNextSync(this, sourceTag = "Login")
                Track.loginSuccessful()

                // signal success
                setResult(Activity.RESULT_OK)
                finish()
            }

            is UserService.LoginResult.Banned -> {
                Track.loginFailed("ban")

                val date = response.ban.endTime?.let { date ->
                    DurationFormat.timeToPointInTime(this, date, short = false)
                }

                val reason = response.ban.reason
                val message = if (date == null) {
                    getString(R.string.banned_forever, reason)
                } else {
                    getString(R.string.banned, date, reason)
                }

                showErrorString(supportFragmentManager, message)

                updateUserCaptcha()
            }

            is UserService.LoginResult.FailureLogin -> {
                Track.loginFailed("generic")

                val msg = getString(R.string.login_not_successful_login)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }

            is UserService.LoginResult.FailureCaptcha -> {
                Track.loginFailed("captcha")

                val msg = getString(R.string.login_not_successful_captcha)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }

            else -> {
                Track.loginFailed("error")

                val msg = getString(R.string.login_not_successful_error)
                showErrorString(supportFragmentManager, msg)

                updateUserCaptcha()
            }
        }
    }

    private fun onRegisterClicked() {
        Track.registerLinkClicked()

        val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
        BrowserHelper.openCustomTab(this, uri)
    }

    private fun onPasswordRecoveryClicked() {
        val intent = Intent(this, RequestPasswordRecoveryActivity::class.java)
        startActivity(intent)
    }

    class DoIfAuthorizedHelper(private val fragment: androidx.fragment.app.Fragment) {
        private var retry: Callback? = null

        fun onActivityResult(requestCode: Int, resultCode: Int) {
            if (requestCode == RequestCodes.AUTHORIZED_HELPER) {
                if (resultCode == Activity.RESULT_OK) {
                    retry?.invoke()
                }

                retry = null
            }
        }

        /**
         * Executes the given runnable if a user is signed in. If not, this method shows
         * the login screen. After a successful login, the given 'retry' runnable will be called.
         */
        private fun runAuth(runnable: Callback, retry: Callback? = null): Boolean {
            val context = fragment.context ?: return false

            val userService: UserService = context.injector.instance()
            return if (userService.isAuthorized) {
                runnable()
                true

            } else {
                this.retry = retry

                val intent = Intent(context, LoginActivity::class.java)
                startActivityForResult(intent, RequestCodes.AUTHORIZED_HELPER)
                false
            }
        }

        fun runAuthWithRetry(runnable: Callback): Boolean {
            return runAuth(runnable, runnable)
        }

        fun runAuth(runnable: Runnable, retry: Runnable? = null): Boolean {
            return runAuth({ runnable.run() }, { retry?.run() })
        }

        suspend fun runAuthSuspend(runnable: suspend () -> Unit): Boolean {
            val context = fragment.context ?: return false
            val userService: UserService = context.injector.instance()

            if (!userService.isAuthorized)
                return false

            runnable()

            return true
        }

        private fun startActivityForResult(intent: Intent, requestCode: Int) {
            fragment.startActivityForResult(intent, requestCode)
        }
    }

    companion object {
        private const val PREF_USERNAME = "LoginDialogFragment.username"

        /**
         * Executes the given runnable if a user is signed in. If not, this method
         * will show a login screen.
         */
        fun helper(fragment: androidx.fragment.app.Fragment) = DoIfAuthorizedHelper(fragment)
    }
}

fun Api.UserCaptcha.decodeImage(context: Context): Drawable {
    val index = image.indexOf(',')
    val offset = if (index < 0) 0 else index + 1

    val bytes = image.substring(offset).decodeBase64(urlSafe = false)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return BitmapDrawable(context.resources, bitmap)
}
