package com.pr0gramm.app.ui

import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.subscribeOnBackground
import okio.ByteString
import rx.Observable
import java.util.concurrent.TimeUnit

/**
 */
class TagSuggestionService(api: Api) {
    private val logger = logger("TagSuggestionService")

    var tags: List<String> = listOf()
        private set

    private var questionableTags: List<String> = listOf()

    init {
        logger.info("Query for tag top- and blacklist")
        api.topTags()
                .subscribeOnBackground()
                .retryWhen { attempts ->
                    attempts.take(5).flatMap {
                        Observable.timer(1, TimeUnit.MINUTES)
                    }
                }
                .subscribe { result ->
                    tags = result.tags
                    questionableTags = result.blacklist

                    logger.info("Cached ${tags.size} tags and ${questionableTags.size} blacklist items.")
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

        val hash = ByteString.encodeString(lower, Charsets.UTF_8)
                .md5().hex().substring(0, 8)

        return hash in questionableTags
    }
}
