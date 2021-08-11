package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.util.getStringOrNull
import java.util.*
import kotlin.reflect.javaType
import kotlin.reflect.typeOf


/**
 * Helps with recent searches
 */

class RecentSearchesServices(
        private val sharedPreferences: SharedPreferences) {

    private val logger = Logger("RecentSearchesServices")
    private val searches: MutableList<String> = ArrayList()

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

    fun searches(): List<String> {
        synchronized(searches) {
            return searches.toList()
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
            val encoded = MoshiInstance.adapter<List<String>>(LIST_OF_STRINGS).toJson(searches)
            sharedPreferences.edit { putString(KEY, encoded) }
        } catch (ignored: Exception) {
            logger.warn { "Could not persist recent searches" }
        }

    }

    private fun restoreState() {
        try {
            val serialized = sharedPreferences.getStringOrNull(KEY) ?: "[]"
            searches.addAll(MoshiInstance.adapter<List<String>>(LIST_OF_STRINGS).fromJson(serialized)
                    ?: listOf())

        } catch (error: Exception) {
            logger.warn("Could not deserialize recent searches", error)
        }

    }

    companion object {
        private const val KEY = "RecentSearchesServices.terms"

        @OptIn(ExperimentalStdlibApi::class)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val LIST_OF_STRINGS = typeOf<java.util.List<String>>().javaType
    }
}
