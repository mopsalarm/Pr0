package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import com.google.android.exoplayer.DecoderInfo;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Ordering;
import com.google.common.io.CharStreams;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;
import rx.Observable;

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
        String version = String.valueOf(AndroidUtility.buildVersionCode());

        return Observable.fromCallable(this::payload)
                .flatMap(logcat -> api.post(name, feedback, version, logcat))
                .subscribeOn(BackgroundScheduler.instance());
    }

    private String payload() {
        try {
            StringBuilder result = new StringBuilder();

            appendDeviceInfo(result);
            result.append("\n\n");

            appendMemoryInfo(result);
            result.append("\n\n");

            appendCodecInfo(result);
            result.append("\n\n");

            appendPreferences(result);
            result.append("\n\n");

            appendLogcat(result);

            // convert result to a string
            return result.toString();

        } catch (Exception err) {
            return "Could not generate logcat: " + err;
        }
    }

    private void appendCodecInfo(StringBuilder result) {
        try {
            List<DecoderInfo> decoderInfos = MediaCodecUtil.getDecoderInfos("video/avc", false);
            for (DecoderInfo info : decoderInfos) {
                result.append("codec: ").append(info.name).append("\n");
            }

        } catch (MediaCodecUtil.DecoderQueryException ignored) {
            result.append("codec: could not query codecs.\n");
        }
    }

    private void appendPreferences(StringBuilder result) {
        //noinspection unchecked
        Iterable<Map.Entry<String, Object>> entries = Ordering.natural()
                .<Map.Entry<String, Object>>onResultOf(Map.Entry::getKey)
                .sortedCopy((Set) Settings.of(context).raw().getAll().entrySet());

        for (Map.Entry<String, Object> entry : entries) {
            if (entry.getKey().startsWith("pref_")) {
                result.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
    }

    @SuppressLint("NewApi")
    private static void appendLogcat(StringBuilder result) throws IOException {
        Process process = Runtime.getRuntime().exec("logcat -d -v threadtime");
        try (Writer writer = CharStreams.asWriter(result)) {
            InputStreamReader reader = new InputStreamReader(process.getInputStream(), Charsets.UTF_8);
            CharStreams.copy(reader, writer);
        } finally {
            try {
                process.destroy();
            } catch (Exception ignored) {
            }
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
