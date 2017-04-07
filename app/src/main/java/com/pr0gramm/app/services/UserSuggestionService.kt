package com.pr0gramm.app.services

import com.google.common.base.Function
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.pr0gramm.app.api.pr0gramm.Api
import org.slf4j.LoggerFactory
import java.util.Collections.emptyList
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class UserSuggestionService @Inject constructor(private val api: Api) {
    private val suggestionCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .build(CacheLoader.from(Function<String, List<String>> {
                internalSuggestUsers(it!!)
            }))

    fun suggestUsers(prefix: String): List<String> {
        return suggestionCache.getUnchecked(prefix.toLowerCase())
    }

    private fun internalSuggestUsers(prefix: String): List<String> {
        if (prefix.length <= 1)
            return emptyList()

        try {
            val response = api.suggestUsers(prefix).execute()
            if (!response.isSuccessful)
                return emptyList()

            return response.body().users().orEmpty()
        } catch (error: Exception) {
            logger.warn("Could not fetch username suggestions for prefix={}: {}", prefix, error)
            return emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("UserSuggestionService")
    }
}
