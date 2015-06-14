package com.pr0gramm.app.services;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.System.currentTimeMillis;

/**
 */
@Singleton
public class HttpProxyService extends NanoHTTPD implements ProxyService {
    private static final Logger logger = LoggerFactory.getLogger(HttpProxyService.class);

    private final String nonce;
    private final OkHttpClient okHttpClient;

    @Inject
    public HttpProxyService(OkHttpClient okHttpClient) {
        super(getRandomPort());

        this.okHttpClient = okHttpClient;
        this.nonce = Hashing.md5().hashLong(currentTimeMillis()).toString();

        logger.info("Open simple proxy on port " + getMyPort());
    }

    /**
     * Tries to get a random free port.
     *
     * @return A random free port number
     */
    private static int getRandomPort() {
        return (int) (20000 + (Math.random() * 40000));
    }

    @Override
    public String proxy(String url) {
        // do not proxy twice!
        if (url.contains(nonce) && url.contains("127.0.0.1"))
            return url;

        String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
        return new Uri.Builder()
                .scheme("http")
                .encodedAuthority("127.0.0.1:" + getMyPort())
                .appendPath(nonce)
                .appendPath(encoded)
                .toString();
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
            String encodedUrl = uri.getLastPathSegment();

            logger.info("Decode {} as utf8 string now", encodedUrl);
            String url = decodedUrl = new String(
                    BaseEncoding.base64Url().decode(encodedUrl),
                    Charsets.UTF_8).trim();

            logger.info("Decoded request to {}", url);

            Request request = buildRequest(url, session);
            com.squareup.okhttp.Response response = okHttpClient.newCall(request).execute();

            Response.IStatus status = translateStatus(response.code(), response.message());
            String contentType = response.header("Content-Type", guessContentType(url));

            InputStream stream;
            final Optional<Integer> length = parseContentLength(response);
            if (length.isPresent()) {
                stream = ByteStreams.limit(new FilterInputStream(response.body().byteStream()) {
                    int read = 0;
                    final int _length = length.get();

                    @Override
                    public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
                        int result = super.read(buffer, byteOffset, byteCount);
                        if (read / 500_000 != (read + result) / 500_000) {
                            int percent = 100 * read / _length;
                            int loaded = (read + result) / 1024;
                            logger.info("Approx {}% loaded ({}, {}kb)", percent, url, loaded);
                        }

                        read += result;
                        if (read == _length) {
                            logger.info("Finished sending file {}", url);
                        }

                        return result;
                    }

                    @Override
                    public int available() throws IOException {
                        // fake a large value here, so ByteStreams.limit can fix it :/
                        return Integer.MAX_VALUE;
                    }
                }, length.get().longValue());
            } else {
                stream = response.body().byteStream();
            }

            Response result = newFixedLengthResponse(status, contentType, stream, length.or(-1));
            result.addHeader("Accept-Range", "bytes");

            // forward content range header
            String contentRange = response.header("Content-Range");
            if (contentRange != null)
                result.addHeader("Content-Range", contentRange);

            if (length.isPresent()) {
                result.addHeader("Content-Length", String.valueOf(length.get()));
            }

            logger.info("Start sending {} ({} kb)", url, length.or(-1) / 1024);
            return result;

        } catch (Exception e) {
            logger.error("Could not proxy for url " + decodedUrl, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
        }
    }

    /**
     * Parses the content length. Will return null, if the length is not parsable
     * or not a positive number.
     *
     * @return The content length.
     */
    private Optional<Integer> parseContentLength(com.squareup.okhttp.Response response) {
        Integer parsed = Ints.tryParse(response.header("Content-Length", ""));
        if (parsed != null && parsed <= 0) {
            parsed = null;
        }

        return Optional.fromNullable(parsed);
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

    private static Request buildRequest(String url, IHTTPSession session) {
        Request.Builder req = new Request.Builder().url(url);

        // forward the range header
        String range = session.getHeaders().get("range");
        if (range != null)
            req = req.addHeader("Range", range);

        return req.build();
    }

    private static Response.IStatus translateStatus(int code, String description) {
        return new Response.IStatus() {
            @Override
            public int getRequestStatus() {
                return code;
            }

            @Override
            public String getDescription() {
                return code + " " + firstNonNull(description, "unknown");
            }
        };
    }
}
