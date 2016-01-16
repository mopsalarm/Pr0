package com.pr0gramm.app.api.categories;

import com.google.gson.Gson;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import retrofit2.GsonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.RxJavaCallAdapterFactory;

/**
 */
@Singleton
public class ExtraCategoryApiProvider implements Provider<ExtraCategoryApi> {
    private final ExtraCategoryApi api;

    @Inject
    public ExtraCategoryApiProvider(OkHttpClient httpClient, Gson gson) {
        this.api = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl("https://pr0.wibbly-wobbly.de/api/categories/v1/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build()
                .create(ExtraCategoryApi.class);
    }

    @Override
    public ExtraCategoryApi get() {
        return api;
    }
}
