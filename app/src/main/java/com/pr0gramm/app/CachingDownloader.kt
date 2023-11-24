package com.pr0gramm.app

import android.net.Uri
import androidx.collection.LruCache
import com.pr0gramm.app.io.Cache
import com.squareup.picasso.Downloader
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class CachingDownloader(private val cache: Cache, private val httpClient: OkHttpClient) : Downloader {
    private val memoryCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    override fun load(request: Request): Response {
        val url = request.url

        // load thumbnails normally
        if (shouldUseMemoryCache(url)) {
            // try memory cache first.
            memoryCache.get(url.toString())?.let { bytes ->
                return byteArrayResponse(request, bytes)
            }

            // do request using fallback - network or disk.
            val response = httpClient.newCall(request).execute()

            // check if we want to cache the response in memory
            val body = response.body
            if (body != null && body.contentLength() in (1 until 20 * 1024)) {
                val bytes = response.use { body.bytes() }

                memoryCache.put(url.toString(), bytes)
                return response.newBuilder()
                        .body(bytes.toResponseBody(body.contentType()))
                        .build()
            }

            return response
        }

        return useCache(request, cache)
    }

    private fun shouldUseMemoryCache(url: HttpUrl): Boolean {
        return url.host == "thumb.pr0gramm.com" || url.toString().endsWith("thumb.jpg")
    }

    private fun useCache(request: Request, cache: Cache): Response {
        cache.get(Uri.parse(request.url.toString())).use { response ->
            return response.toResponse(request)
        }
    }

    private fun byteArrayResponse(request: Request, body: ByteArray): Response {
        return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_0)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("image/jpeg".toMediaTypeOrNull()))
                .build()
    }

    override fun shutdown() {
        memoryCache.evictAll()
    }
}