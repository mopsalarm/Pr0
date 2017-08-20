package com.pr0gramm.app.services

import android.support.v4.util.LruCache
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.getOrPut
import org.slf4j.LoggerFactory
import java.util.Collections.emptyList

/**
 */
class UserSuggestionService(private val api: Api) {
    private val logger = LoggerFactory.getLogger("UserSuggestionService")
    private val suggestionCache = LruCache<String, List<String>>(128)

    fun suggestUsers(prefix: String): List<String> {
        return suggestionCache.getOrPut(prefix.toLowerCase()) {
            internalSuggestUsers(it)
        }
    }

    private fun internalSuggestUsers(prefix: String): List<String> {
        if (prefix.length <= 1)
            return emptyList()

        logger.info("Looking for users starting with prefix {}", prefix)
        try {
            val response = api.suggestUsers(prefix).execute()
            if (!response.isSuccessful)
                return emptyList()

            return response.body()?.users.orEmpty()
        } catch (error: Exception) {
            logger.warn("Could not fetch username suggestions for prefix={}: {}", prefix, error)
            return emptyList()
        }
    }
}
