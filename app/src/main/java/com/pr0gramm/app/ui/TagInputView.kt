package com.pr0gramm.app.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView
import com.google.common.base.Charsets
import com.google.common.io.CharStreams
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

/**
 */
object TagInputView {
    private val logger = LoggerFactory.getLogger("TagInputView")

    // holds the list of tags
    private var TAGS: List<String>? = null

    /**
     * Parses the list of tags. It is provided as one tag per line in utf8 encoding.
     */
    @JvmStatic
    private fun loadTagJson(context: Context): List<String> {
        try {
            return context.assets.open("tags.txt").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    CharStreams.readLines(reader).mapNotNull { line ->
                        line.trim().takeIf { it.length > 1 }
                    }
                }
            }
        } catch (error: Exception) {
            logger.error("Could not load list of tags", error)
            return emptyList()
        }
    }

    @JvmStatic
    fun setup(tagInput: MultiAutoCompleteTextView) {
        if (TAGS == null) {
            // load the tags from the compressed json file
            TAGS = loadTagJson(tagInput.context)
        }

        // get the auto-suggestion list.
        val adapter = ArrayAdapter(tagInput.context,
                android.R.layout.simple_dropdown_item_1line,
                TAGS!!)

        tagInput.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        tagInput.setAdapter(adapter)
    }

}
