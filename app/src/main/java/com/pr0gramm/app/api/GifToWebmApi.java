package com.pr0gramm.app.api;

import retrofit.http.GET;
import retrofit.http.Path;
import rx.Observable;

/**
 * Simple gif-to-webm service.
 */
public interface GifToWebmApi {
    public static final String ENDPOINT = "http://128.199.53.54:5000";

    @GET("/status")
    Observable<StatusResponse> alive();

    @GET("/convert/{url}")
    Observable<ConvertResult> convert(@Path("url") String encodedUrl);

    public static class StatusResponse {
        private boolean alive;

        public boolean isAlive() {
            return alive;
        }
    }

    public static class ConvertResult {
        private String path;

        public String getPath() {
            return path;
        }
    }
}
