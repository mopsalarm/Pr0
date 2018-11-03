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
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.FeedbackService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.matches
import com.pr0gramm.app.util.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotterknife.bindView
import kotterknife.bindViews
import org.kodein.di.erased.instance

/**
 */
class ContactActivity : BaseAppCompatActivity("ContactActivity") {
    private val contactService: ContactService by instance()
    private val feedbackService: FeedbackService by instance()
    private val userService: UserService by instance()

    private val buttonSubmit: Button by bindView(R.id.submit)
    private val vName: EditText by bindView(R.id.feedback_name)
    private val vText: EditText by bindView(R.id.feedback_text)
    private val vMail: EditText by bindView(R.id.feedback_email)
    private val vSubject: EditText by bindView(R.id.feedback_subject)

    private val choiceApp: RadioButton by bindView(R.id.action_feedback_app)
    private val choiceGeneral: RadioButton by bindView(R.id.action_feedback_general)

    private val groupAllTextViews: List<TextView> by bindViews(R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_text)

    private val groupAll: List<View> by bindViews(R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_deletion_hint, R.id.feedback_type_app_hint)
    private val groupNormalSupport: List<View> by bindViews(R.id.feedback_email, R.id.feedback_subject, R.id.feedback_deletion_hint)
    private val groupAppLoggedIn: List<View> by bindViews(R.id.feedback_type_app_hint)
    private val groupAppNotLoggedIn: List<View> by bindViews(R.id.feedback_name, R.id.feedback_type_app_hint)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_feedback)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val primary = ContextCompat.getColor(this, ThemeHelper.accentColor)
        ViewCompat.setBackgroundTintList(buttonSubmit, ColorStateList.valueOf(primary))

        // register all the change listeners
        for (textView in groupAllTextViews) {
            textView.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateSubmitButtonActivation()
                }
            })
        }

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
            isNormalSupport -> groupNormalSupport
            userService.isAuthorized -> groupAppLoggedIn
            else -> groupAppNotLoggedIn
        }

        for (view in groupAll) {
            view.visibility = if (activeViews.contains(view)) View.VISIBLE else View.GONE
        }

        updateSubmitButtonActivation()
    }

    private fun updateSubmitButtonActivation() {
        var enabled = groupAllTextViews.none { it.visible && it.text.toString().trim().isEmpty() }

        if (vMail.visible && !vMail.text.matches(Patterns.EMAIL_ADDRESS)) {
            enabled = false
        }

        buttonSubmit.isEnabled = enabled
    }

    private fun submitClicked() {
        // hide keyboard when sending
        AndroidUtility.hideSoftKeyboard(vText)

        launchWithErrorHandler(busyDialog = true) {
            withViewDisabled(buttonSubmit) {
                sendFeedback()
                onSubmitSuccess()
            }
        }
    }

    private suspend fun sendFeedback() {
        var feedback = vText.text.toString().trim()
        if (isNormalSupport) {
            val email = vMail.text.toString().trim()
            val subject = vSubject.text.toString().trim()

            feedback += "\n\nGesendet mit der App v" + BuildConfig.VERSION_NAME

            withContext(Dispatchers.IO + NonCancellable) {
                contactService.post(email, subject, feedback)
            }

        } else {
            val name = userService.name ?: vName.text.toString().trim()

            withContext(Dispatchers.IO + NonCancellable) {
                feedbackService.post(name, feedback)
            }
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
