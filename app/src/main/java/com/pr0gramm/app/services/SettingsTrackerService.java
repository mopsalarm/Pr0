package com.pr0gramm.app.services;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.pr0gramm.app.Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import proguard.annotation.KeepPublicClassMemberNames;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

@Singleton
public class SettingsTrackerService {
    static final Logger logger = LoggerFactory.getLogger("SettingsTrackerService");

    private final Settings settings;
    private final HttpInterface httpInterface;

    @Inject
    public SettingsTrackerService(Settings settings, OkHttpClient httpClient, Gson gson) {
        this.settings = settings;

        this.httpInterface = new Retrofit.Builder()
                .baseUrl("https://pr0.wibbly-wobbly.de/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .validateEagerly(true)
                .build()
                .create(HttpInterface.class);
    }

    public void track() {
        ImmutableMap<String, ?> values = ImmutableMap.copyOf(
                Maps.filterKeys(settings.raw().getAll(), key -> key.startsWith("pref_")));

        // track the object!
        httpInterface.track(ImmutableMap.of("settings", values)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                logger.info("Tracked settings successfully.");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable err) {
                logger.error("Could not track settings", err);
            }
        });
    }

    @KeepPublicClassMemberNames
    private interface HttpInterface {
        @POST("track-settings")
        Call<Void> track(@Body Map<String, Object> values);
    }
}
