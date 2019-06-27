package com.pr0gramm.app.ui

import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.retryUpTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encode

/**
 */
class TagSuggestionService(api: Api) {
    private val logger = Logger("TagSuggestionService")

    var tags: List<String> = listOf()
        private set

    private var questionableTags: List<String> = listOf()

    init {
        logger.info { "Query for tag top- and blacklist" }

        AsyncScope.launch {
            retryUpTo(5, { delay(60 * 1000) }) {
                val result = api.topTagsAsync()

                tags = result.tags
                questionableTags = result.blacklist

                logger.info { "Cached ${tags.size} tags and ${questionableTags.size} blacklist items." }
            }
        }
    }

    fun setupView(tagInput: MultiAutoCompleteTextView) {
        val adapter = ArrayAdapter(tagInput.context,
                android.R.layout.simple_dropdown_item_1line,
                tags)

        tagInput.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        tagInput.setAdapter(adapter)
    }

    fun containsQuestionableTag(input: CharSequence): Boolean {
        val tags = input.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }
        return tags.any { isQuestionableTag(it) }
    }

    private fun isQuestionableTag(tag: CharSequence): Boolean {
        val lower = tag.toString().toLowerCase()

        val hash = lower.encode(Charsets.UTF_8)
                .md5().hex().substring(0, 8)

        return hash in questionableTags
    }
}
