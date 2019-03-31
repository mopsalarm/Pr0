package com.pr0gramm.app.ui.views

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.widget.AppCompatCheckBox
import com.pr0gramm.app.R
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.ui.RecentSearchesAutoCompleteAdapter
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotterknife.bindView
import rx.Observable
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

    private var pendingState: Bundle? = null

    val initView = Once {
        View.inflate(context, R.layout.view_search, this)

        minimumScoreSlider.max = 1000
        minimumScoreSlider.keyProgressIncrement = 5

        if (!isInEditMode) {
            minimumScoreLabel.text = formatMinimumScoreValue(0)

            // update the value field with the slider
            minimumScoreSlider.setOnProgressChanged { value, _ ->
                minimumScoreLabel.text = formatMinimumScoreValue(roundScoreValue(value))
            }

            val editorListener = TextView.OnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    handleSearchButtonClicked()
                }

                true
            }

            // enter on search field should start the search
            searchTermView.setOnEditorActionListener(editorListener)
            customExcludesView.setOnEditorActionListener(editorListener)
        }

        find<View>(R.id.reset_button).setOnClickListener { reset() }
        find<View>(R.id.search_cancel).setOnClickListener { cancel() }
        find<View>(R.id.search_advanced).setOnClickListener { showAdvancedHelpPage() }
        find<View>(R.id.search_button).setOnClickListener { handleSearchButtonClicked() }

        if (!isInEditMode) {
            initAutoCompleteView(context.injector.instance())
        }

        if (this.pendingState != null) {
            this.applyState(pendingState)
            this.pendingState = null
        } else {
            updateTagsCheckboxes()
        }
    }

    private fun initAutoCompleteView(recentSearchesServices: RecentSearchesServices) {
        (searchTermView as? AutoCompleteTextView)?.setAdapter(
                RecentSearchesAutoCompleteAdapter(recentSearchesServices,
                        context, android.R.layout.simple_dropdown_item_1line))
    }

    fun setQueryHint(hint: String) {
        initView()
        searchTermView.hint = hint
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
        BrowserHelper.openCustomTab(context, uri)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        super.onRestoreInstanceState(null)

        if (state is Bundle) {
            applyState(state)
        }
    }

    fun currentState(): Bundle? {
        if (!initView.initialized)
            return null

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

        if (!initView.initialized) {
            this.pendingState = state
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
        if (baseTerm.startsWith("?") || baseTerm.startsWith("!")) {
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
        if (terms.all { term -> term.all { it.isWhitespace() || it.isLetterOrDigit() } }) {
            extendedSearch = false
        }

        // combine everything together
        var searchTerm = terms.joinToString(" & ")
        if (extendedSearch || terms.size > 1) {
            searchTerm = "! $searchTerm"
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
        customExcludesView.text.toString().toLowerCase()
                .split("\\s+".toPattern())
                .filterTo(withoutTags) { it != "" }

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
        params.rightMargin = context.dip2px(8)

        val names = listOf("sound", "video", "repost", "ftb")
        val tags = listOf("f:sound", "video", "f:repost", "m:ftb")

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
                    ?.let { AndroidUtility.screenIsLandscape(it) }
                    ?: false

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

        fun ofQueryTerm(queryTerm: String): Bundle {
            val bundle = Bundle()
            bundle.putCharSequence("queryTerm", queryTerm)
            return bundle
        }
    }
}

class Once(private val block: () -> Unit) {
    var initialized = false
        private set

    operator fun invoke() {
        if (!initialized) {
            initialized = true
            block()
        }
    }
}
