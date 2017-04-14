package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Strings
import com.jakewharton.rxbinding.widget.textChanges
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.ThemeHelper.primaryColorDark
import com.pr0gramm.app.services.ThemeHelper.theme
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.sync.SyncJob
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.AndroidUtility.toObservable
import com.pr0gramm.app.util.CustomTabsHelper
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.use
import com.trello.rxlifecycle.android.ActivityEvent
import kotterknife.bindView
import net.danlew.android.joda.DateUtils
import org.joda.time.DateTimeZone
import org.joda.time.Weeks
import retrofit2.HttpException
import rx.Observable
import rx.functions.Action1

/**
 */
class LoginActivity : BaseAppCompatActivity() {
    private val userService: UserService by instance()
    private val prefs: SharedPreferences by instance()

    private val usernameView: EditText by bindView(R.id.username)
    private val passwordView: EditText by bindView(R.id.password)
    private val submitView: Button by bindView(R.id.login)

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(theme().whiteAccent)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // restore last username
        val defaultUsername = prefs.getString(PREF_USERNAME, "")
        if (!Strings.isNullOrEmpty(defaultUsername)) {
            usernameView.setText(defaultUsername)
        }

        submitView.setOnClickListener { onLoginClicked() }

        find<View>(R.id.register).setOnClickListener { onRegisterClicked() }
        find<View>(R.id.password_recovery).setOnClickListener { onPasswordRecoveryClicked() }

        updateActivityBackground()

        Observable.combineLatest(
                usernameView.textChanges().map { it.trim().length },
                passwordView.textChanges().map { it.trim().length },
                { username, password -> username * password })
                .subscribe { value -> submitView.isEnabled = value > 0 }

    }

    override fun injectComponent(component: ActivityComponent) {
    }

    private fun updateActivityBackground() {
        @DrawableRes
        val drawableId = theme.obtainStyledAttributes(R.style.AppTheme, intArrayOf(R.attr.loginBackground)).use {
            it.getResourceId(R.styleable.AppTheme_loginBackground, 0)
        }

        if (drawableId == 0)
            return

        val fallbackColor = ContextCompat.getColor(this, primaryColorDark())
        val background = createBackgroundDrawable(drawableId, fallbackColor)
        AndroidUtility.setViewBackground(findViewById(R.id.content), background)
    }

    private fun createBackgroundDrawable(drawableId: Int, fallbackColor: Int): Drawable {
        val background: Drawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            background = WrapCrashingDrawable(fallbackColor,
                    ResourcesCompat.getDrawable(resources, drawableId, theme)!!)
        } else {
            background = ColorDrawable(fallbackColor)
        }
        return background
    }


    private fun enableView(enable: Boolean) {
        usernameView.isEnabled = enable
        passwordView.isEnabled = enable
        submitView.isEnabled = enable
    }

    private fun onLoginClicked() {
        val username = usernameView.text.toString()
        val password = passwordView.text.toString()

        if (username.isEmpty()) {
            usernameView.error = getString(R.string.must_not_be_empty)
            return
        }

        if (password.isEmpty()) {
            passwordView.error = getString(R.string.must_not_be_empty)
            return
        }

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply()

        userService.login(username, password)
                .compose(bindUntilEventAsync(ActivityEvent.DESTROY))
                .lift(busyDialog(this, getString(R.string.login_please_wait)))
                .flatMap { progress -> toObservable(progress.getLogin()) }
                .interceptLoginFailures()
                .doOnSubscribe { enableView(false) }
                .doOnError { enableView(true) }
                .subscribe(Action1 { this.handleLoginResult(it) }, defaultOnError())
    }

    private fun Observable<Api.Login>.interceptLoginFailures(): Observable<LoginResult> {
        return this.
                map { response ->
                    when {
                        response.success() -> LoginResult.Success()
                        response.banInfo().isPresent -> LoginResult.Banned(response.banInfo().get())
                        else -> LoginResult.Failure()
                    }
                }
                .onErrorResumeNext { err ->
                    if (err is HttpException && err.code() == 403) {
                        Observable.just(LoginResult.Failure())
                    } else {
                        Observable.error(err)
                    }
                }
    }

    private fun handleLoginResult(response: LoginResult) {
        when (response) {
            is LoginResult.Success -> {
                SyncJob.scheduleNextSync()
                Track.loginSuccessful()

                // signal success
                setResult(Activity.RESULT_OK)
                finish()
            }

            is LoginResult.Banned -> {
                val date = DateUtils.getRelativeDateTimeString(this,
                        response.ban.till().toDateTime(DateTimeZone.getDefault()),
                        Weeks.ONE,
                        DateUtils.FORMAT_SHOW_DATE)

                val reason = response.ban.reason()
                showErrorString(supportFragmentManager, getString(R.string.banned, date, reason))
            }

            is LoginResult.Failure -> {
                Track.loginFailed()

                val msg = getString(R.string.login_not_successful)
                showErrorString(supportFragmentManager, msg)
                enableView(true)
            }
        }
    }

    fun onRegisterClicked() {
        Track.registerLinkClicked()

        val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap")
        CustomTabsHelper(this).openCustomTab(uri)
    }

    fun onPasswordRecoveryClicked() {
        val intent = Intent(this, RequestPasswordRecoveryActivity::class.java)
        startActivity(intent)
    }

    abstract class DoIfAuthorizedHelper protected constructor() {
        private var retry: Runnable? = null

        fun onActivityResult(requestCode: Int, resultCode: Int) {
            if (requestCode == RequestCodes.AUTHORIZED_HELPER) {
                if (resultCode == Activity.RESULT_OK) {
                    retry?.run()
                }

                retry = null
            }
        }

        /**
         * Executes the given runnable if a user is signed in. If not, this method shows
         * the login screen. After a successful login, the given 'retry' runnable will be called.
         */
        @JvmOverloads fun run(runnable: Runnable, retry: Runnable? = null): Boolean {
            val context = context ?: return false

            val userService = Dagger.appComponent(context).userService()
            if (userService.isAuthorized) {
                runnable.run()
                return true

            } else {
                this.retry = retry

                val intent = Intent(context, LoginActivity::class.java)
                startActivityForResult(intent, RequestCodes.AUTHORIZED_HELPER)
                return false
            }
        }

        protected abstract val context: Context?

        protected abstract fun startActivityForResult(intent: Intent, requestCode: Int)
    }

    private sealed class LoginResult {
        class Success() : LoginResult()
        class Banned(val ban: Api.Login.BanInfo) : LoginResult()
        class Failure() : LoginResult()
    }

    companion object {
        private const val PREF_USERNAME = "LoginDialogFragment.username"

        /**
         * Executes the given runnable if a user is signed in. If not, this method
         * will show a login screen.
         */
        fun helper(fragment: Fragment): DoIfAuthorizedHelper {
            return object : DoIfAuthorizedHelper() {
                override val context: Context?
                    get() = fragment.context

                override fun startActivityForResult(intent: Intent, requestCode: Int) {
                    fragment.startActivityForResult(intent, requestCode)
                }
            }
        }
    }
}
