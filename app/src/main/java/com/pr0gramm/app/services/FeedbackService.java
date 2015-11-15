package com.pr0gramm.app.services;

import android.content.Context;
import android.os.Build;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.okhttp.OkHttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
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
    private final Context context;

    @Inject
    public FeedbackService(OkHttpClient okHttpClient, Context context) {
        this.context = context;
        this.api = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("http://pr0.wibbly-wobbly.de/api/feedback/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build().create(Api.class);

    }

    public Observable<Nothing> post(String name, String feedback) {
        String version = String.valueOf(AndroidUtility.getPackageVersionCode(context));

        return Async
                .start(FeedbackService::logcat, BackgroundScheduler.instance())
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
            result.append("Flavor: ").append(BuildConfig.FLAVOR)
                    .append("(").append(BuildConfig.APPLICATION_ID).append(")\n");

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
        @POST("post")
        Observable<Nothing> post(@Field("name") String name,
                                 @Field("feedback") String feedback,
                                 @Field("version") String version,
                                 @Field("logcat") String logcat);
    }
}
