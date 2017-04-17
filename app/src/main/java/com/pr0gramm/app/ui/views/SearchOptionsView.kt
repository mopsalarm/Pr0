package com.pr0gramm.app.ui.views

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.widget.AppCompatCheckBox
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.google.common.base.CharMatcher.javaLetterOrDigit
import com.google.common.base.CharMatcher.whitespace
import com.google.common.base.Splitter
import com.jakewharton.rxbinding.widget.changes
import com.jakewharton.rxbinding.widget.editorActions
import com.pr0gramm.app.R
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.ui.RecentSearchesAutoCompleteAdapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.CustomTabsHelper
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.map
import kotterknife.bindView
import rx.Observable
import rx.functions.Func1
import rx.subjects.PublishSubject

/**
 * View for more search options.
 */

class SearchOptionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val searchQuery = PublishSubject.create<SearchQuery>()
    private val searchCanceled = PublishSubject.create<Boolean>()

    private val excludedTags = hashSetOf<String>()

    private val searchTermView: EditText by bindView(R.id.search_term)
    private val minimumScoreLabel: TextView by bindView(R.id.minimum_benis_label)
    private val minimumScoreSlider: SeekBar by bindView(R.id.minimum_benis_slider)
    private val customExcludesView: EditText by bindView(R.id.without_tags_text)

    init {
        View.inflate(context, R.layout.view_search, this)

        updateTagsCheckboxes()

        minimumScoreSlider.max = 1000
        minimumScoreSlider.keyProgressIncrement = 5

        if (!isInEditMode) {
            // update the value field with the slider
            minimumScoreSlider.changes()
                    .map { value -> formatMinimumScoreValue(roundScoreValue(value)) }
                    .subscribe { minimumScoreLabel.text = it }

            // enter on search field should start the search
            searchTermView
                    .editorActions(Func1 { action -> action == EditorInfo.IME_ACTION_SEARCH })
                    .subscribe { handleSearchButtonClicked() }

            // and start search on custom tags view too.
            customExcludesView
                    .editorActions(Func1 { action -> action == EditorInfo.IME_ACTION_SEARCH })
                    .subscribe { handleSearchButtonClicked() }
        }

        find<View>(R.id.reset_button).setOnClickListener { reset() }
        find<View>(R.id.search_cancel).setOnClickListener { cancel() }
        find<View>(R.id.search_advanced).setOnClickListener { showAdvancedHelpPage() }
        find<View>(R.id.search_button).setOnClickListener { handleSearchButtonClicked() }
    }

    fun setupAutoComplete(recentSearchesServices: RecentSearchesServices) {
        (searchTermView as? AutoCompleteTextView)?.setAdapter(
                RecentSearchesAutoCompleteAdapter(recentSearchesServices,
                        context, android.R.layout.simple_dropdown_item_1line))
    }

    var queryHint: String
        get() = searchTermView.hint.toString()
        set(queryHint) {
            searchTermView.hint = queryHint
        }


    /**
     * Resets the view back to its "empty" state.
     */
    private fun reset() {
        searchTermView.setText("")
        customExcludesView.setText("")
        minimumScoreSlider.progress = 0

        excludedTags.clear()
        updateTagsCheckboxes()
    }

    private fun cancel() {
        searchCanceled.onNext(true)
    }

    private fun showAdvancedHelpPage() {
        val uri = Uri.parse("https://github.com/mopsalarm/pr0gramm-tags/blob/master/README.md#tag-suche-für-pr0gramm")
        CustomTabsHelper(context).openCustomTab(uri)
    }

    override fun onSaveInstanceState(): Parcelable {
        super.onSaveInstanceState()
        return currentState()
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        super.onRestoreInstanceState(null)

        if (state is Bundle) {
            applyState(state)
        }
    }

    fun currentState(): Bundle {
        return Bundle().apply {
            putInt("minScore", minimumScoreSlider.progress)
            putCharSequence("queryTerm", searchTermView.text)
            putCharSequence("customWithoutTerm", customExcludesView.text)
            putStringArray("selectedWithoutTags", excludedTags.toTypedArray())
        }
    }

    fun applyState(state: Bundle?) {
        if (state == null) {
            return
        }

        minimumScoreSlider.progress = state.getInt("minScore", 0)
        searchTermView.setText(state.getCharSequence("queryTerm", ""))
        customExcludesView.setText(state.getCharSequence("customWithoutTerm", ""))

        // clear original tags
        excludedTags.clear()

        // set new tags
        excludedTags += state.getStringArray("selectedWithoutTags") ?: emptyArray()

        // rebuild the checkboxes
        updateTagsCheckboxes()
    }

    private fun handleSearchButtonClicked() {
        var extendedSearch = false

        val terms = mutableListOf<String>()

        // get the base search-term
        var baseTerm = searchTermView.text.toString().trim()
        if (baseTerm.startsWith("?")) {
            extendedSearch = true
            baseTerm = baseTerm.substring(1).trim()
        }

        if (!baseTerm.isEmpty()) {
            terms.add(baseTerm)
        }

        // add minimum benis score selector
        val score = roundScoreValue(minimumScoreSlider.progress)
        if (score > 0) {
            extendedSearch = true
            terms.add(String.format("s:%d", score))
        }

        // add tags to ignore
        val withoutTags = buildCurrentExcludedTags()
        if (withoutTags.isNotEmpty()) {
            extendedSearch = true
            terms.add(String.format("-(%s)", withoutTags.joinToString("|")))
        }

        // empty or actually simple search?
        if (terms.all { javaLetterOrDigit().or(whitespace()).matchesAllOf(it) }) {
            extendedSearch = false
        }

        // combine everything together
        var searchTerm = terms.joinToString(" & ")
        if (extendedSearch || terms.size > 1) {
            searchTerm = "? " + searchTerm
        }

        // replace all new line characters (why would you add a new line?)
        searchTerm = searchTerm.replace('\n', ' ')

        searchQuery.onNext(SearchQuery(searchTerm, baseTerm))
    }

    private fun roundScoreValue(score: Int): Int {
        val result = (Math.pow(score / 100.0, 2.0) * 90).toInt()
        return (0.5 + result / 100.0).toInt() * 100
    }

    private fun formatMinimumScoreValue(score: Int): String {
        val formatted = if (score == 0) {
            context.getString(R.string.search_score_ignored)
        } else {
            score.toString()
        }

        return context.getString(R.string.search_score, formatted)
    }

    private fun buildCurrentExcludedTags(): Set<String> {
        // use tags from check-boxes
        val withoutTags = this.excludedTags.toHashSet()

        // add custom tags
        withoutTags.addAll(Splitter
                .on(whitespace()).trimResults().omitEmptyStrings()
                .splitToList(customExcludesView.text.toString().toLowerCase()))

        return withoutTags
    }

    fun searchQuery(): Observable<SearchQuery> {
        return searchQuery
    }

    fun searchCanceled(): Observable<Boolean> {
        return searchCanceled
    }

    private fun updateTagsCheckboxes() {
        val container = find<ViewGroup>(R.id.without_checks)

        container.removeAllViews()

        val params = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        params.rightMargin = AndroidUtility.dp(context, 8)

        val tags = listOf("f:sound", "webm", "f:repost", "m:ftb")
        val names = listOf("sound", "webm", "repost", "ftb")

        for (idx in tags.indices) {
            val tagValue = tags[idx]

            val checkbox = AppCompatCheckBox(context)
            checkbox.text = names[idx]
            checkbox.isChecked = excludedTags.contains(tagValue)
            checkbox.layoutParams = params

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    excludedTags.add(tagValue)
                } else {
                    excludedTags.remove(tagValue)
                }
            }

            container.addView(checkbox)
        }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, 0, right, bottom)

        // move top padding to search view (container)
        val container: View? = findViewById(R.id.search_term_container)
        container?.setPadding(0, top, 0, 0)
    }

    fun requestSearchFocus() {
        post {
            val landscape = AndroidUtility.activityFromContext(context)
                    .map { AndroidUtility.screenIsLandscape(it) }
                    .or(false)

            if (landscape) {
                searchTermView.requestFocus()
            } else {
                AndroidUtility.showSoftKeyboard(searchTermView)
            }
        }
    }

    class SearchQuery(val combined: String, val queryTerm: String)

    companion object {
        /**
         * Creates a new state containing the given query term.
         */
        @JvmStatic
        fun ofQueryTerm(queryTerm: String): Bundle {
            val bundle = Bundle()
            bundle.putCharSequence("queryTerm", queryTerm)
            return bundle
        }
    }
}
