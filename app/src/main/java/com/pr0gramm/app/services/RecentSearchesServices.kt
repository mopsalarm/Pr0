package com.pr0gramm.app.services

import android.content.SharedPreferences
import com.google.common.collect.ImmutableList
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.pr0gramm.app.util.edit
import org.slf4j.LoggerFactory
import java.util.*


/**
 * Helps with recent searches
 */

class RecentSearchesServices(
        private val sharedPreferences: SharedPreferences,
        private val gson: Gson) {

    private val searches: MutableList<String> = ArrayList<String>()

    init {
        restoreState()
    }

    fun storeTerm(term: String) {
        synchronized(searches) {
            removeCaseInsensitive(term)
            searches.add(0, term)

            persistStateAsync()
        }
    }

    fun searches(): ImmutableList<String> {
        synchronized(searches) {
            return ImmutableList.copyOf(searches)
        }
    }

    fun clearHistory() {
        synchronized(searches) {
            searches.clear()
            persistStateAsync()
        }
    }

    /**
     * Removes all occurrences of the given term, independend of case.
     */
    private fun removeCaseInsensitive(term: String) {
        searches.removeAll { it.equals(term, ignoreCase = true) }
    }

    private fun persistStateAsync() {
        try {
            // write searches as json
            val encoded = gson.toJson(searches, LIST_OF_STRINGS.type)
            sharedPreferences.edit() { putString(KEY, encoded) }
        } catch (ignored: Exception) {
            logger.warn("Could not presist recent searches")
        }

    }

    private fun restoreState() {
        try {
            val serialized = sharedPreferences.getString(KEY, "[]")
            searches.addAll(gson.fromJson<Collection<String>>(serialized, LIST_OF_STRINGS.type))

        } catch (error: Exception) {
            logger.warn("Could not deserialize recent searches", error)
        }

    }

    companion object {
        private val logger = LoggerFactory.getLogger("RecentSearchesServices")

        private val KEY = "RecentSearchesServices.terms"
        private val LIST_OF_STRINGS = object : TypeToken<List<String>>() {}
    }
}
