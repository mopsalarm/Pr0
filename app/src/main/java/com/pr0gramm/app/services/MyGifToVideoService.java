package com.pr0gramm.app.services;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.squareup.okhttp.OkHttpClient;

import retrofit.RestAdapter;
import retrofit.android.AndroidLog;
import retrofit.client.OkClient;
import retrofit.http.GET;
import retrofit.http.Path;
import rx.Observable;

/**
 * Converts a gif to a webm using my own conversion service.
 */
public class MyGifToVideoService implements GifToVideoService {
    private static final String DEFAULT_ENDPOINT = "http://128.199.53.54:5000";

    private final Api api;

    public MyGifToVideoService(OkHttpClient client) {
        this.api = new RestAdapter.Builder()
                .setEndpoint(DEFAULT_ENDPOINT)
                .setLog(new AndroidLog("Gif2Webm"))
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
    private static interface Api {
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
