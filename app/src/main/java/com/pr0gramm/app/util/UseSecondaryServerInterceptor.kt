package com.pr0gramm.app.util

import com.pr0gramm.app.Settings
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class UseSecondaryServerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var req = chain.request()

        if (Settings.useSecondaryServers) {
            req = req.newBuilder()
                .url(injectSecondaryServer(req.url))
                .build()
        }

        return chain.proceed(req)
    }

    private fun injectSecondaryServer(url: HttpUrl): HttpUrl {
        return when (url.host) {
            "img.pr0gramm.com" -> url.newBuilder().host("images.pr0gramm.com").build()
            "vid.pr0gramm.com" -> url.newBuilder().host("videos.pr0gramm.com").build()
            "full.pr0gramm.com" -> url.newBuilder().host("fullsize.pr0gramm.com").build()
            else -> url
        }
    }
}
