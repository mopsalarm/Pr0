package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.okhttp.OkHttpClient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Modifier;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.POST;
import rx.Observable;
import rx.util.async.Async;

import static java.util.Arrays.asList;

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
                .baseUrl("https://pr0.wibbly-wobbly.de/api/feedback/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build().create(Api.class);

    }

    public Observable<Nothing> post(String name, String feedback) {
        String version = String.valueOf(AndroidUtility.getPackageVersionCode(context));

        return Async
                .start(FeedbackService::payload, BackgroundScheduler.instance())
                .flatMap(logcat -> api.post(name, feedback, version, logcat));
    }

    private static String payload() {
        try {
            StringBuilder result = new StringBuilder();

            appendDeviceInfo(result);
            result.append("\n\n");

            appendMemoryInfo(result);
            result.append("\n\n");

            appendLogcat(result);

            // convert result to a string
            return result.toString();

        } catch (Exception err) {
            return "Could not generate logcat: " + err;
        }
    }

    @SuppressLint("NewApi")
    private static void appendLogcat(StringBuilder result) throws IOException {
        Process process = Runtime.getRuntime().exec("logcat -d -v threadtime");
        try (Writer writer = CharStreams.asWriter(result)) {
            InputStreamReader reader = new InputStreamReader(process.getInputStream(), Charsets.UTF_8);
            CharStreams.copy(reader, writer);
        } finally {
            process.destroy();
        }
    }

    private static void appendDeviceInfo(StringBuilder result) {
        result.append("Android: ").append(Build.VERSION.RELEASE).append('\n');
        result.append("Flavor: ").append(BuildConfig.FLAVOR)
                .append("(").append(BuildConfig.APPLICATION_ID).append(")\n");

        for (java.lang.reflect.Field field : Build.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    String name = field.getName().toLowerCase().replace('_', ' ');
                    Object value = formatValue(field.get(null));
                    result.append(name).append(" = ").append(value).append("\n");
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String formatValue(Object value) {
        if (value instanceof String[]) {
            return asList((String[]) value).toString();
        } else {
            return String.valueOf(value);
        }
    }

    private static void appendMemoryInfo(StringBuilder result) {
        Runtime rt = Runtime.getRuntime();
        result.append("Memory used: ").append(rt.totalMemory() / 1024 / 1024).append("mb\n");
        result.append("MaxMemory for this app: ").append(rt.maxMemory() / 1024 / 1024).append("mb\n");
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
