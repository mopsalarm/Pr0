package com.pr0gramm.app.api.gif2webm;

import retrofit.http.GET;
import retrofit.http.Path;
import rx.Observable;

/**
 * Simple gif-to-webm service.
 */
public interface Api {
    public static final String DEFAULT_ENDPOINT = "http://128.199.53.54:5000";

    @GET("/status")
    Observable<StatusResponse> alive();

    @GET("/convert/{url}")
    Observable<ConvertResult> convert(@Path("url") String encodedUrl);
}
