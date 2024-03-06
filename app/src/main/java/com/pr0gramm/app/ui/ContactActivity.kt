package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import com.pr0gramm.app.databinding.ActivityFeedbackBinding
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.hideSoftKeyboard
import com.pr0gramm.app.util.matches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class ContactActivity : BaseAppCompatActivity("ContactActivity") {
    private val contactService: ContactService by instance()
    private val userService: UserService by instance()

    private val views by bindViews(ActivityFeedbackBinding::inflate)

    private val groupAllInputViews: List<TextView>
        get() = listOf(views.feedbackEmail, views.feedbackName, views.feedbackSubject, views.feedbackText)

    private val groupAll: List<View>
        get() = listOf(views.feedbackEmail, views.feedbackName, views.feedbackSubject, views.feedbackDeletionHint)

    private val groupNormalLoggedIn: List<View>
        get() = listOf(views.feedbackSubject, views.feedbackDeletionHint)

    private val groupNormalLoggedOut: List<View>
        get() = listOf(views.feedbackEmail, views.feedbackSubject, views.feedbackDeletionHint)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(views)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // val primary = ContextCompat.getColor(this, ThemeHelper.accentColor)
        // ViewCompat.setBackgroundTintList(views.submit, ColorStateList.valueOf(primary))

        // register all the change listeners
        for (textView in groupAllInputViews) {
            textView.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateSubmitButtonActivation()
                }
            })
        }

        Linkify.linkify(views.feedbackDeletionHint)

        views.submit.setOnClickListener { submitClicked() }

        views.faqCategory.adapter = CategoriesAdapter(this)
        views.faqCategory.setSelection(0)

        views.faqCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSubmitButtonActivation()
            }
        }

        userService.name?.let { views.feedbackName.setText(it) }

        applyViewVisibility()
    }

    private class CategoriesAdapter(context: Context) : ArrayAdapter<Category>(
            context, android.R.layout.simple_spinner_dropdown_item, faqCategories) {

        override fun isEnabled(position: Int): Boolean = position > 0

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return super.getDropDownView(position, convertView, parent).also { view ->
                view.isEnabled = position > 0
            }
        }
    }

    private fun applyViewVisibility() {
        val activeViews: List<View> = when {
            userService.isAuthorized -> groupNormalLoggedIn
            else -> groupNormalLoggedOut
        }

        for (view in groupAll) {
            view.isVisible = view in activeViews
        }

        updateSubmitButtonActivation()
    }

    private fun updateSubmitButtonActivation() {
        // the button is enabled if there is no visible text view that has
        // an empty input.
        var enabled = groupAllInputViews.none { view ->
            view.isVisible && view.text.toString().isBlank()
        }

        if (views.feedbackEmail.isVisible && !views.feedbackEmail.text.matches(Patterns.EMAIL_ADDRESS)) {
            enabled = false
        }

        val faqCategory = views.faqCategory.selectedItem as Category
        if (faqCategory.category == "none") {
            enabled = false
        }

        views.submit.isEnabled = enabled
    }

    private fun submitClicked() {
        // hide keyboard when sending
        hideSoftKeyboard()

        launchWhenStarted(busyIndicator = true) {
            withViewDisabled(views.submit) {
                sendFeedback()
                onSubmitSuccess()
            }
        }
    }

    private suspend fun sendFeedback() {
        val category = views.faqCategory.selectedItem as Category
        val email = views.feedbackEmail.text.toString().trim()
        val feedback = views.feedbackText.text.toString().trim()
        var subject = views.feedbackSubject.text.toString().trim()

        if (category.category == "app") {
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

private class Category(val category: String, val text: String) {
    override fun toString(): String = text
}

private val faqCategories = listOf(
    Category("none", "Kategorie auswählen"),
    Category("app", "App"),
    Category("pr0gramm", "pr0gramm"),
)
