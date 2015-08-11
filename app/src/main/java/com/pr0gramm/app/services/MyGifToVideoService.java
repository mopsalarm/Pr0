package com.pr0gramm.app.services;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.LoggerAdapter;
import com.squareup.okhttp.OkHttpClient;

import org.slf4j.LoggerFactory;

import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.http.GET;
import retrofit.http.Path;
import rx.Observable;

/**
 * Converts a gif to a webm using my own conversion service.
 */
@Singleton
public class MyGifToVideoService implements GifToVideoService {
    private static final String DEFAULT_ENDPOINT = "http://pr0.wibbly-wobbly.de/api/gif-to-webm/v1";

    private final Api api;

    @Inject
    public MyGifToVideoService(OkHttpClient client) {
        this.api = new RestAdapter.Builder()
                .setEndpoint(DEFAULT_ENDPOINT)
                .setLog(new LoggerAdapter(LoggerFactory.getLogger(Api.class)))
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .setClient(new OkClient(client))
                .build()
                .create(Api.class);
    }

    /**
     * Converts this gif into a webm, if possible.
     *
     * @param gifUrl The url of the gif to convert.
     * @return A observable producing exactly one item with the result.
     */
    @Override
    public Observable<Result> toVideo(String gifUrl) {
        Observable<Result> fallback = Observable.just(new Result(gifUrl));
        if (!gifUrl.toLowerCase().endsWith(".gif")) {
            return fallback;
        }

        String encoded = BaseEncoding.base64Url().encode(gifUrl.getBytes(Charsets.UTF_8));
        return api.convert(encoded)
                .map(result -> new Result(gifUrl, DEFAULT_ENDPOINT + result.getPath()))
                .onErrorResumeNext(fallback);
    }

    /**
     * Simple gif-to-webm service.
     */
    private interface Api {
        @GET("/convert/{url}")
        Observable<ConvertResult> convert(@Path("url") String encodedUrl);
    }

    /**
     */
    private static class ConvertResult {
        private String path;

        public String getPath() {
            return path;
        }
    }
}
