package com.pr0gramm.app.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.util.Patterns
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding.widget.checkedChanges
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.FeedbackService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.util.*
import kotterknife.bindView
import kotterknife.bindViews
import rx.Observable

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

    private val generalFeedbackRadioButton: RadioButton by bindView(R.id.action_feedback_general)

    private val groupAllTextViews: List<TextView> by bindViews(R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_text)

    private val groupAll: List<View> by bindViews(R.id.feedback_email, R.id.feedback_name, R.id.feedback_subject, R.id.feedback_deletion_hint)
    private val groupNormalSupport: List<View> by bindViews(R.id.feedback_email, R.id.feedback_subject, R.id.feedback_deletion_hint)
    private val groupAppNotLoggedIn: List<View> by bindViews(R.id.feedback_name)

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

        Observable.merge(
                find<RadioButton>(R.id.action_feedback_app).checkedChanges(),
                find<RadioButton>(R.id.action_feedback_general).checkedChanges())
                .subscribe { applyViewVisibility() }

        applyViewVisibility()
    }

    private val isNormalSupport: Boolean
        get() {
            return generalFeedbackRadioButton.isChecked
        }

    private fun applyViewVisibility() {
        val activeViews: List<View> = when {
            isNormalSupport -> groupNormalSupport
            userService.isAuthorized -> emptyList()
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
        var feedback = vText.text.toString().trim()

        val response = if (isNormalSupport) {
            val email = vMail.text.toString().trim()
            val subject = vSubject.text.toString().trim()

            feedback += "\n\nGesendet mit der App v" + BuildConfig.VERSION_NAME

            contactService.post(email, subject, feedback)
        } else {
            val name = userService.name ?: vName.text.toString().trim()
            feedbackService.post(name, feedback)
        }

        response.decoupleSubscribe()
                .compose(bindToLifecycleAsync<Any>())
                .withBusyDialog(this)
                .subscribeWithErrorHandling(onComplete = { onSubmitSuccess() })

        // hide keyboard if still open
        AndroidUtility.hideSoftKeyboard(vText)
    }

    private fun onSubmitSuccess() {
        showDialog(this) {
            content(R.string.feedback_sent)
            positive(R.string.okay) { finish() }
            onCancel { finish() }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
