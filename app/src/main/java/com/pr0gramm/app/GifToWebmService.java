package com.pr0gramm.app;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.pr0gramm.app.api.GifToWebmApi;

import javax.inject.Inject;

import rx.Observable;

/**
 */
public class GifToWebmService {
    private final GifToWebmApi api;

    @Inject
    public GifToWebmService(GifToWebmApi api) {
        this.api = api;
    }

    /**
     * Converts this gif into a webm, if possible.
     *
     * @param url The url of the gif to convert.
     * @return A observable producing exactly one item with the result.
     */
    public Observable<Result> convertToWebm(String url) {
        Observable<Result> fallback = Observable.just(new Result(url, false));
        if (!url.toLowerCase().endsWith(".gif")) {
            return fallback;
        }

        String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
        return api.convert(encoded)
                .map(result -> new Result(GifToWebmApi.ENDPOINT + result.getPath(), true))
                .onErrorResumeNext(fallback);
    }

    /**
     * Checks if the service is alive.
     * Returns an observable that produces exactly one value. True, if the
     * service is currently alive, false otherwise.
     */
    public Observable<Boolean> isAlive() {
        return api.alive()
                .map(GifToWebmApi.StatusResponse::isAlive)
                .onErrorResumeNext(Observable.just(Boolean.FALSE));
    }

    public final static class Result {
        private final String url;
        private final boolean webm;

        public Result(String url, boolean webm) {
            this.url = url;
            this.webm = webm;
        }

        public String getUrl() {
            return url;
        }

        public boolean isWebm() {
            return webm;
        }
    }
}
