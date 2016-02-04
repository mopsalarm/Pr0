package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.pr0gramm.app.util.BackgroundScheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import rx.Observable;
import rx.functions.Func1;

/**
 *
 */
@Singleton
public class FeatureFlags {
    private static final Logger logger = LoggerFactory.getLogger("FeatureFlags");
    private final SharedPreferences preferences;

    @Inject
    public FeatureFlags(SharedPreferences preferences, OkHttpClient okHttpClient, Gson gson) {
        this.preferences = preferences;


        Observable<Map<String, Object>> rxFeatureFile = featureFile(okHttpClient, gson);
        Observable.interval(3, TimeUnit.HOURS, BackgroundScheduler.instance())
                .flatMap(ignored -> rxFeatureFile
                        .doOnError(error -> logger.warn("Could not get feature file", error))
                        .onErrorResumeNext(Observable.empty()))
                .subscribe(this::updateFromFeatureFile);
    }

    private void updateFromFeatureFile(Map<String, Object> features) {
        SharedPreferences.Editor editor = preferences.edit();
        try {
            for (Map.Entry<String, Object> entry : features.entrySet()) {
                String key = key(entry.getKey());
                String value = String.valueOf(entry.getValue());
                editor.putString(key, value);
            }
        } finally {
            editor.apply();
        }
    }

    public <T> T get(String name, T defaultValue, Func1<String, T> converter) {
        String key = key(name);
        String value = preferences.getString(key, null);
        return value == null ? defaultValue : converter.call(value);
    }

    private boolean active(String key) {
        return random(key) < this.get(key, 0.f, Float::valueOf);
    }

    private float random(String name) {
        String key = key(name) + ".random";
        synchronized (preferences) {
            float result = preferences.getFloat(key, -1);
            if (result < 0) {
                result = (float) Math.random();
                preferences.edit()
                        .putFloat(key, result)
                        .apply();
            }

            return result;
        }
    }

    private String key(String name) {
        return "featureFlag." + name;
    }

    public boolean softwareDecoderOverrideThreshold() {
        return active("softwareDecoderOverride");
    }

    private static Observable<Map<String, Object>> featureFile(OkHttpClient okHttpClient, Gson gson) {
        FeatureFlagApi api = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("https://app.pr0gramm.com/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .validateEagerly(true)
                .build()
                .create(FeatureFlagApi.class);

        return Observable.fromCallable(api::get);
    }

    private interface FeatureFlagApi {
        @GET("feature-flags.json")
        Map<String, Object> get();
    }
}
