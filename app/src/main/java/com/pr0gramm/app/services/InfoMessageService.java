package com.pr0gramm.app.services;

import android.support.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.gson.GsonBuilder;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import rx.Observable;

/**
 * Gets a short info message. This might be used to inform about
 * server failures.
 */
@Singleton
@Gson.TypeAdapters
public class InfoMessageService {
    private final Api api;

    @Inject
    public InfoMessageService(OkHttpClient okHttpClient) {
        GsonConverterFactory converterFactory = GsonConverterFactory.create(new GsonBuilder()
                .registerTypeAdapterFactory(new GsonAdaptersInfoMessageService())
                .create());

        this.api = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("https://pr0.wibbly-wobbly.de/")
                .addConverterFactory(converterFactory)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .validateEagerly(true)
                .build()
                .create(Api.class);
    }

    /**
     * Returns an observable that might produce a mesage, if one is available.
     */
    public Observable<String> infoMessage() {
        return api.get()
                .map(Response::message)
                .filter(message -> !Strings.isNullOrEmpty(message));
    }

    private interface Api {
        @GET("info-message.json")
        Observable<Response> get();
    }

    @Value.Immutable
    interface Response {
        @Nullable
        String message();
    }
}
