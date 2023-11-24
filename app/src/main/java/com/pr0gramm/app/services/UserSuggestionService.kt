package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.getOrPut
import kotlinx.coroutines.runBlocking
import java.util.Collections.emptyList
import java.util.Locale

/**
 */
class UserSuggestionService(private val api: Api) {
    private val logger = Logger("UserSuggestionService")
    private val suggestionCache = androidx.collection.LruCache<String, List<String>>(128)

    fun suggestUsers(prefix: String): List<String> {
        return suggestionCache.getOrPut(prefix.lowercase(Locale.getDefault())) {
            internalSuggestUsers(it)
        }
    }

    private fun internalSuggestUsers(prefix: String): List<String> {
        if (prefix.length <= 1)
            return emptyList()

        logger.info { "Looking for users starting with prefix $prefix" }
        try {
            val response = runBlocking { api.suggestUsers(prefix) }
            return response.users

        } catch (error: Exception) {
            logger.warn { "Could not fetch username suggestions for prefix=$prefix: $error" }
            return emptyList()
        }
    }
}
