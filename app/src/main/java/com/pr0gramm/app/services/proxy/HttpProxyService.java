package com.pr0gramm.app.services.proxy;

import android.net.Uri;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.pr0gramm.app.io.Cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import fi.iki.elonen.NanoHTTPD;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.pr0gramm.app.util.AndroidUtility.toFile;
import static java.lang.System.currentTimeMillis;

/**
 */
@Singleton
public class HttpProxyService extends NanoHTTPD implements ProxyService {
    private static final Logger logger = LoggerFactory.getLogger("HttpProxyService");

    private static final Response ERROR_RESPONSE = newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR, "text/plain", null);

    private static final Response ERROR_RESPONSE_NOT_FOUND = newFixedLengthResponse(
            Response.Status.NOT_FOUND, "text/plain", null);

    private final String nonce;
    private final Cache cache;
    private final int port;

    @Inject
    public HttpProxyService(Cache cache) {
        this(cache, getRandomPort());
    }

    private HttpProxyService(Cache cache, int port) {
        super("127.0.0.1", port);
        this.cache = cache;

        this.port = port;
        this.nonce = Hashing.md5().hashLong(currentTimeMillis()).toString();
        logger.info("Open simple proxy on port " + port);
    }

    /**
     * Tries to get a random free port.
     *
     * @return A random free port number
     */
    private static int getRandomPort() {
        return (int) (10000 + (Math.random() * 20000));
    }

    @Override
    public Uri proxy(Uri uri) {
        // do not proxy twice!
        String uriString = uri.toString();
        if (uriString.contains(nonce) && uriString.contains("127.0.0.1"))
            return uri;

        // append the name at the end of the generated uri.
        String name = firstNonNull(uri.getLastPathSegment(), "name");

        String encoded = BaseEncoding.base64Url().encode(uriString.getBytes(Charsets.UTF_8));
        return new Uri.Builder()
                .scheme("http")
                .encodedAuthority("127.0.0.1:" + port)
                .appendPath(nonce)
                .appendPath(encoded)
                .appendPath(name)
                .build();
    }

    @Override
    public Response serve(IHTTPSession session) {
        logger.info("New request for {}", session.getUri());

        Uri uri = Uri.parse(session.getUri());
        if (!nonce.equals(Iterables.getFirst(uri.getPathSegments(), null))) {
            logger.info("Got request with invalid nonce: {}", uri);
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "");
        }

        String decodedUrl = null;
        try {
            String encodedUrl = uri.getPathSegments().get(1);

            logger.info("Decode {} as utf8 string now", encodedUrl);
            String url = decodedUrl = new String(
                    BaseEncoding.base64Url().decode(encodedUrl),
                    Charsets.UTF_8).trim();

            logger.info("Decoded request to {}", url);
            return proxyUri(session, url);

        } catch (Throwable error) {
            logger.error("Could not proxy for url " + decodedUrl, error);
            return ERROR_RESPONSE;
        }
    }

    private Response proxyUri(IHTTPSession session, String url) throws IOException {
        return url.matches("https?://.*") ? proxyHttpUri(session, url) : proxyFileUri(toFile(Uri.parse(url)));
    }

    private Response proxyFileUri(File file) throws FileNotFoundException {
        if (!file.exists())
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "");

        long size = file.length();
        FileInputStream stream = new FileInputStream(file);
        Response response = newFixedLengthResponse(Response.Status.OK,
                guessContentType(file.toString()), stream, size);

        response.setGzipEncoding(false);
        response.setChunkedTransfer(false);
        return response;
    }

    private Response proxyHttpUri(IHTTPSession session, final String url) throws IOException {
        Cache.Entry entry = cache.entryOf(Uri.parse(url));

        int totalSize = entry.totalSize();

        ContentRange range = null;
        String rangeValue = session.getHeaders().get("Range");
        if (rangeValue != null) {
            range = ContentRange.parse(rangeValue, totalSize);
        }

        InputStream input = entry.inputStreamAt(range != null ? range.start : 0);
        String contentType = guessContentType(url);

        Response.Status status;
        int contentLength;
        if (range != null) {
            status = Response.Status.PARTIAL_CONTENT;
            contentLength = range.length();

            // limit the input stream to the content amount
            input = ByteStreams.limit(input, contentLength);
        } else {
            status = Response.Status.OK;
            contentLength = totalSize;
        }

        Response result = newFixedLengthResponse(status, contentType, input, totalSize);
        result.setGzipEncoding(false);
        result.setChunkedTransfer(false);
        result.addHeader("Accept-Ranges", "bytes");
        result.addHeader("Cache-Content", "no-cache");
        result.addHeader("Content-Length", String.valueOf(contentLength));

        if (range != null) {
            result.addHeader("Content-Range", String.format("bytes %d-%d/%d", range.start, range.end, totalSize));
        }

        logger.info("Start sending {} ({} kb)", url, totalSize / 1024);
        return result;
    }

    private static class ContentRange {
        final int start;
        final int end;

        private ContentRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int length() {
            return 1 + end - start;
        }

        public static ContentRange parse(String inputValue, int totalSize) {
            Matcher matcher = Pattern.compile("^(\\d*)-(\\d*)").matcher(inputValue);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Could not parse content range from input.");
            }

            int start = 0;
            if (matcher.group(1).length() > 0) {
                start = Integer.parseInt(matcher.group(1), 10);
            }

            int end = totalSize - 1;
            if (matcher.group(2).length() > 0) {
                end = Integer.parseInt(matcher.group(2), 10);
            }

            return new ContentRange(start, end);
        }
    }

    /**
     * Guess a content type from the URL.
     *
     * @param url The url to guess the content stream for.
     */
    private static String guessContentType(String url) {
        url = url.toLowerCase();
        if (url.endsWith(".webm")) {
            return "video/webm";
        } else if (url.endsWith(".mp4")) {
            return "video/mp4";
        } else if (url.matches(".*\\.jpe?g")) {
            return "image/jpeg";
        } else if (url.endsWith(".png")) {
            return "image/png";
        } else if (url.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream";
        }
    }
}
