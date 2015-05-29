package com.pr0gramm.app.services;

import android.content.Context;
import android.os.Build;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.feed.Nothing;
import com.squareup.okhttp.OkHttpClient;

import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.POST;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

/**
 * A simple service to generate and send a feedback to the feedback server.
 */
@Singleton
public class FeedbackService {
    private final Api api;

    @Inject
    public FeedbackService(OkHttpClient okHttpClient) {
        this.api = new RestAdapter.Builder()
                .setClient(new OkClient(okHttpClient))
                .setEndpoint("http://pr0.wibbly-wobbly.de:5002")
                .build().create(Api.class);
    }

    public Observable<Nothing> post(Context context, String name, String feedback) {
        String version = Pr0grammApplication.getPackageInfo().versionName;

        return Async
                .start(FeedbackService::logcat, Schedulers.io())
                .flatMap(logcat -> api.post(name, feedback, version, logcat));
    }

    private static String logcat() {
        try {
            StringBuilder result = new StringBuilder();
            result.append("Brand: ").append(Build.BRAND).append('\n');
            result.append("Product: ").append(Build.PRODUCT).append('\n');
            result.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n');
            result.append("Model: ").append(Build.MODEL).append('\n');
            result.append("Display: ").append(Build.DISPLAY).append('\n');
            result.append("Android: ").append(Build.VERSION.RELEASE).append('\n');
            result.append("\n\n");

            Process process = Runtime.getRuntime().exec("logcat -d -v threadtime");
            try {
                byte[] bytes = ByteStreams.toByteArray(process.getInputStream());
                return result.append(new String(bytes, Charsets.UTF_8)).toString();

            } finally {
                process.destroy();
            }

        } catch (Exception err) {
            return "Could not generate logcat: " + err;
        }
    }

    private interface Api {
        @FormUrlEncoded
        @POST("/post")
        Observable<Nothing> post(@Field("name") String name,
                                 @Field("feedback") String feedback,
                                 @Field("version") String version,
                                 @Field("logcat") String logcat);
    }
}
