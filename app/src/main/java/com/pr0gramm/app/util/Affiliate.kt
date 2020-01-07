package com.pr0gramm.app.util

import android.content.Context
import android.util.Base64
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.config.ConfigService

object Affiliate {
    private val reAffiliate by memorize<Context, Regex> { context ->
        try {
            ConfigService.get(context).reAffiliate.toRegex()
        } catch (err: Exception) {
            AndroidUtility.logToCrashlytics(err)

            // need to return something
            Config().reAffiliate.toRegex()
        }
    }

    fun get(context: Context, url: String): String? {
        return if (reAffiliate(context).containsMatchIn(url)) {
            val encoded = Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE)
            return "https://app.pr0gramm.com/redirect.html?u=$encoded"
        } else {
            null
        }
    }
}