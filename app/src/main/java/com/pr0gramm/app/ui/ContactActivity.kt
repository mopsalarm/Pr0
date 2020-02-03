package com.pr0gramm.app.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Patterns
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.matches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotterknife.bindView
import kotterknife.bindViews

/**
 */
class ContactActivity : BaseAppCompatActivity("ContactActivity") {
    private val contactService: ContactService by instance()
    private val userService: UserService by instance()

    private val buttonSubmit: Button by bindView(R.id.submit)
    private val vName: EditText by bindView(R.id.feedback_name)
    private val vText: EditText by bindView(R.id.feedback_text)
    private val vMail: EditText by bindView(R.id.feedback_email)
    private val vSubject: EditText by bindView(R.id.feedback_subject)

    private val choiceApp: RadioButton by bindView(R.id.action_feedback_app)
    private val choiceGeneral: RadioButton by bindView(R.id.action_feedback_general)

    private val groupAllInputViews: List<TextView> by bindViews(R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_text)

    private val groupAll: List<View> by bindViews(R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_deletion_hint)
    private val groupNormalLoggedIn: List<View> by bindViews(R.id.feedback_subject, R.id.feedback_deletion_hint)
    private val groupNormalLoggedOut: List<View> by bindViews(R.id.feedback_email, R.id.feedback_subject, R.id.feedback_deletion_hint)
    private val groupAppLoggedIn: List<View> by bindViews(R.id.feedback_subject)
    private val groupAppLoggedOut: List<View> by bindViews(R.id.feedback_email, R.id.feedback_subject, R.id.feedback_name)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_feedback)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val primary = ContextCompat.getColor(this, ThemeHelper.accentColor)
        ViewCompat.setBackgroundTintList(buttonSubmit, ColorStateList.valueOf(primary))

        // register all the change listeners
        for (textView in groupAllInputViews) {
            textView.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateSubmitButtonActivation()
                }
            })
        }

        Linkify.linkify(find(R.id.feedback_deletion_hint))

        userService.name?.let { vName.setText(it) }

        find<View>(R.id.submit).setOnClickListener { submitClicked() }

        listOf(choiceApp, choiceGeneral).forEach { button ->
            button.setOnCheckedChangeListener { _, _ -> applyViewVisibility() }
        }

        applyViewVisibility()
    }

    private val isNormalSupport: Boolean
        get() {
            return choiceGeneral.isChecked
        }

    private fun applyViewVisibility() {
        val activeViews: List<View> = when {
            isNormalSupport && userService.isAuthorized -> groupNormalLoggedIn
            isNormalSupport -> groupNormalLoggedOut
            userService.isAuthorized -> groupAppLoggedIn
            else -> groupAppLoggedOut
        }

        for (view in groupAll) {
            view.isVisible = view in activeViews
        }

        updateSubmitButtonActivation()
    }

    private fun updateSubmitButtonActivation() {
        // the button is enabled if there is no visible text view that has
        // an empty input.
        var enabled = groupAllInputViews.none {
            it.isVisible && it.text.toString().trim().isEmpty()
        }

        if (vMail.isVisible && !vMail.text.matches(Patterns.EMAIL_ADDRESS)) {
            enabled = false
        }

        buttonSubmit.isEnabled = enabled
    }

    private fun submitClicked() {
        // hide keyboard when sending
        AndroidUtility.hideSoftKeyboard(vText)

        launchWhenStarted(busyIndicator = true) {
            withViewDisabled(buttonSubmit) {
                sendFeedback()
                onSubmitSuccess()
            }
        }
    }

    private suspend fun sendFeedback() {
        val email = vMail.text.toString().trim()
        val feedback = vText.text.toString().trim()

        var subject = vSubject.text.toString().trim()
        if (!isNormalSupport) {
            subject = "[app] $subject"
        }

        withContext(Dispatchers.IO + NonCancellable) {
            contactService.post(email, subject, feedback)
        }
    }

    private fun onSubmitSuccess() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
