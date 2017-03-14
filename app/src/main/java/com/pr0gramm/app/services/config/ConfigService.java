package com.pr0gramm.app.services.config;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.gson.Gson;
import com.pr0gramm.app.BuildConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * Simple config service to do remove configuration with local fallback
 */
@Singleton
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger("ConfigService");
    private static final String PREF_KEY = "ConfigService.data";

    private final Subject<Config, Config> configSubject = BehaviorSubject.<Config>create(
            ImmutableConfig.builder().build()).toSerialized();

    private final Gson gson;
    private final OkHttpClient okHttpClient;
    private final SharedPreferences preferences;

    private volatile Config configState;

    @Inject
    public ConfigService(OkHttpClient okHttpClient, Gson gson, SharedPreferences preferences) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.preferences = preferences;

        String jsonCoded = preferences.getString(PREF_KEY, "{}");
        this.configState = loadState(gson, jsonCoded);

        publishState();

        // schedule updates once an hour
        Observable.interval(0, 1, TimeUnit.HOURS, Schedulers.io()).subscribe(event -> update());
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void update() {
        try {
            Request request = new Request.Builder()
                    .url("http://pr0.wibbly-wobbly.de/app-config/" + BuildConfig.VERSION_CODE + "/config.json")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    updateConfigState(body);

                } else {
                    logger.warn("Could not update app-config, response code {}", response.code());
                }
            }

        } catch (Exception err) {
            logger.warn("Could not update app-config", err);
        }
    }

    private void updateConfigState(String body) {
        configState = loadState(gson, body);
        persistConfigState();
        publishState();
    }

    private void persistConfigState() {
        logger.info("Persisting current config state");
        try {
            String jsonCoded = gson.toJson(configState);
            preferences.edit()
                    .putString(PREF_KEY, jsonCoded)
                    .apply();

        } catch (Exception err) {
            logger.warn("Could not persist config state", err);
        }
    }

    private void publishState() {
        logger.info("Publishing change in config state");
        try {
            configSubject.onNext(configState);
        } catch (Exception err) {
            logger.warn("Could not publish the current state Oo", err);
        }
    }

    /**
     * Observes the config. The config changes are not observed on any particual thread.
     */
    public Observable<Config> observeConfig() {
        return configSubject;
    }

    public Config config() {
        return configState;
    }

    private static Config loadState(Gson gson, String jsonCoded) {
        try {
            return gson.fromJson(jsonCoded, Config.class);
        } catch (Exception err) {
            logger.warn("Could not decode state", err);
            return ImmutableConfig.builder().build();
        }
    }
}
