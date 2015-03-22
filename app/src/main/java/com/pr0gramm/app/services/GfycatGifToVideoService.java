package com.pr0gramm.app.services;

import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import retrofit.RestAdapter;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 * Uses Gfycat to convert gifs to webm files.
 */
public class GfycatGifToVideoService implements GifToVideoService {
    private final GfycatApi gfycatApi;

    public GfycatGifToVideoService() {
        this.gfycatApi = new RestAdapter.Builder()
                .setEndpoint("http://upload.gfycat.com/")
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .build()
                .create(GfycatApi.class);
    }

    @Override
    public Observable<Result> toVideo(String gifUrl) {
        Observable<Result> fallback = Observable.just(new Result(gifUrl));
        if (!gifUrl.toLowerCase().endsWith(".gif")) {
            return fallback;
        }

        String encoded = BaseEncoding.base64Url().encode(gifUrl.getBytes(Charsets.UTF_8));
        return gfycatApi.transcode(encoded)
                .flatMap(result -> {
                    if (result.error != null) {
                        Log.w("GfyCat", "Could not convert: " + result.error);
                        return fallback;
                    } else {
                        return Observable.just(new Result(gifUrl, result.mp4Url));
                    }
                })
                .onErrorResumeNext(fallback);
    }


    private interface GfycatApi {
        @GET("/transcode")
        Observable<TranscodeResponse> transcode(@Query("fetchUrl") String fetchUrl);
    }

    private static class TranscodeResponse {
        String error;
        String mp4Url;
    }
}
