package com.pr0gramm.app.services.proxy

import android.net.Uri
import com.google.common.base.Charsets
import com.google.common.base.MoreObjects.firstNonNull
import com.google.common.collect.Iterables
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import com.google.common.io.ByteStreams
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.util.AndroidUtility.toFile
import fi.iki.elonen.NanoHTTPD
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.lang.System.currentTimeMillis
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class HttpProxyService private constructor(
        private val cache: Cache,
        private val port: Int = HttpProxyService.makeRandomPort()) : NanoHTTPD("127.0.0.1", port), ProxyService {

    private val nonce: String = Hashing.md5().hashLong(currentTimeMillis()).toString()

    @Inject
    constructor(cache: Cache) : this(cache, makeRandomPort()) {
    }

    override fun proxy(url: Uri): Uri {
        // do not proxy twice!
        val uriString = url.toString()
        if (uriString.contains(nonce) && uriString.contains("127.0.0.1"))
            return url

        // append the name at the end of the generated uri.
        val name = firstNonNull(url.lastPathSegment, "name")

        val encoded = BaseEncoding.base64Url().encode(uriString.toByteArray(Charsets.UTF_8))
        return Uri.Builder()
                .scheme("http")
                .encodedAuthority("127.0.0.1:" + port)
                .appendPath(nonce)
                .appendPath(encoded)
                .appendPath(name)
                .build()
    }

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        logger.info("New request for {}", session.uri)

        val uri = Uri.parse(session.uri)
        if (nonce != Iterables.getFirst(uri.pathSegments, null)) {
            logger.info("Got request with invalid nonce: {}", uri)
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, "text/plain", "")
        }

        var decodedUrl: String? = null
        try {
            val encodedUrl = uri.pathSegments[1]

            logger.info("Decode {} as utf8 string now", encodedUrl)
            decodedUrl = String(BaseEncoding.base64Url().decode(encodedUrl), Charsets.UTF_8).trim()

            val url = decodedUrl

            logger.info("Decoded request to {}", url)
            return proxyUri(session, url)

        } catch (error: Throwable) {
            logger.error("Could not proxy for url " + decodedUrl!!, error)
            return ERROR_RESPONSE
        }

    }

    private fun proxyUri(session: NanoHTTPD.IHTTPSession, url: String): NanoHTTPD.Response {
        return if (url.matches("https?://.*".toRegex())) proxyHttpUri(session, url) else proxyFileUri(toFile(Uri.parse(url)))
    }

    private fun proxyFileUri(file: File): NanoHTTPD.Response {
        if (!file.exists())
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "")

        val size = file.length()
        val stream = FileInputStream(file)
        val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                guessContentType(file.toString()), stream, size)

        response.setGzipEncoding(false)
        response.setChunkedTransfer(false)
        return response
    }

    private fun proxyHttpUri(session: NanoHTTPD.IHTTPSession, url: String): NanoHTTPD.Response {
        cache.get(Uri.parse(url)).use { entry ->
            val totalSize = entry.totalSize()

            val rangeValue = session.headers["Range"]

            val range: ContentRange? = rangeValue?.let {
                parseContentRange(it, totalSize)
            }

            var input = entry.inputStreamAt(range?.start ?: 0)
            val contentType = guessContentType(url)

            val status: Response.Status
            val contentLength: Int
            if (range != null) {
                status = NanoHTTPD.Response.Status.PARTIAL_CONTENT
                contentLength = range.length

                // limit the input stream to the content amount
                input = ByteStreams.limit(input, contentLength.toLong())
            } else {
                status = NanoHTTPD.Response.Status.OK
                contentLength = totalSize
            }

            val result = NanoHTTPD.newFixedLengthResponse(status, contentType, input, totalSize.toLong())
            result.setGzipEncoding(false)
            result.setChunkedTransfer(false)
            result.addHeader("Accept-Ranges", "bytes")
            result.addHeader("Cache-Content", "no-cache")
            result.addHeader("Content-Length", contentLength.toString())

            if (range != null) {
                result.addHeader("Content-Range", String.format("bytes %d-%d/%d", range.start, range.end, totalSize))
            }

            logger.info("Start sending {} ({} kb)", url, totalSize / 1024)
            return result
        }
    }

    private class ContentRange(val start: Int, val end: Int) {
        val length: Int = 1 + end - start
    }

    private fun parseContentRange(inputValue: String, totalSize: Int): ContentRange {
        val matcher = Pattern.compile("^(\\d*)-(\\d*)").matcher(inputValue)
        if (!matcher.find()) {
            throw IllegalArgumentException("Could not parse content range from input.")
        }

        var start = 0
        if (matcher.group(1).isNotEmpty()) {
            start = Integer.parseInt(matcher.group(1), 10)
        }

        var end = totalSize - 1
        if (matcher.group(2).isNotEmpty()) {
            end = Integer.parseInt(matcher.group(2), 10)
        }

        return ContentRange(start, end)
    }

    companion object {
        private val logger = LoggerFactory.getLogger("HttpProxyService")

        private val ERROR_RESPONSE = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", null)

        /**
         * Tries to get a random free port.

         * @return A random free port number
         */
        private fun makeRandomPort(): Int = (10000 + Math.random() * 20000).toInt()

        /**
         * Guess a content type from the URL.

         * @param url The url to guess the content stream for.
         */
        private fun guessContentType(url: String): String {
            url.toLowerCase().let { url ->
                when {
                    url.endsWith(".webm") -> return "video/webm"
                    url.endsWith(".mp4") -> return "video/mp4"
                    url.endsWith(".png") -> return "image/png"
                    url.endsWith(".gif") -> return "image/gif"
                    url.endsWith(".jpg") -> return "image/jpeg"
                    url.endsWith(".jpeg") -> return "image/jpeg"
                    else -> return "application/octet-stream"
                }
            }
        }
    }
}
