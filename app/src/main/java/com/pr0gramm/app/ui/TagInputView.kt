package com.pr0gramm.app.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.directKodein
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import org.kodein.di.erased.instance
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.InputStreamReader

/**
 */
object TagInputView {
    private val logger = LoggerFactory.getLogger("TagInputView")

    /**
     * Parses the list of tags. It is provided as one tag per line in utf8 encoding.
     */
    private fun loadTagJson(context: Context): List<String> {
        val config = context.directKodein.instance<Config>()

        val questionableTags = config.questionableTags.map { it.toLowerCase() }

        try {
            return logger.time("Loading tags from file") {
                context.assets.open("tags.txt").use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        reader.readLines().mapNotNull { line ->
                            line.trim().takeIf { tag ->
                                val lower = tag.toLowerCase()
                                tag.length > 1 && questionableTags.none { lower.contains(it) }
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            logger.error("Could not load list of tags", error)
            return emptyList()
        }
    }

    private lateinit var TAGS: Observable<List<String>>


    fun initialize(context: Context) {
        TAGS = Observable
                .fromCallable { loadTagJson(context.applicationContext) }
                .subscribeOn(BackgroundScheduler.instance())
                .cache()
    }

    fun setup(tagInput: MultiAutoCompleteTextView) {
        TAGS.first().observeOn(AndroidSchedulers.mainThread()).bindToLifecycle(tagInput).subscribe { tags ->
            val adapter = ArrayAdapter(tagInput.context,
                    android.R.layout.simple_dropdown_item_1line,
                    tags)

            tagInput.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
            tagInput.setAdapter(adapter)
        }
    }
}
