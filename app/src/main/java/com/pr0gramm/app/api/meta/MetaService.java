package com.pr0gramm.app.api.meta;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.services.Graph;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;

/**
 */
@Singleton
public class MetaService {
    private final MetaApi api;

    @Inject
    public MetaService(OkHttpClient httpClient) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new GsonAdaptersMetaApi())
                .create();

        api = new Retrofit.Builder()
                .baseUrl("https://pr0.wibbly-wobbly.de/api/meta/v1/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .validateEagerly(true)
                .build()
                .create(MetaApi.class);
    }

    public Observable<MetaApi.UserInfo> getUserInfo(String username) {
        return api.user(username);
    }

    public Observable<Graph> getBenisHistory(String username) {
        return getUserInfo(username)
                .filter(info -> info.getBenisHistory().size() > 10)
                .map(info -> {
                    List<MetaApi.BenisHistoryEntry> history = Ordering.natural()
                            .onResultOf(MetaApi.BenisHistoryEntry::getTimestamp)
                            .sortedCopy(info.getBenisHistory());

                    ImmutableList<Graph.Point> points = FluentIterable.from(history)
                            .transform(e -> new Graph.Point(e.getTimestamp(), e.getBenis()))
                            .toList();

                    long now = System.currentTimeMillis() / 1000;
                    return new Graph(points.get(0).x, now, points);
                });
    }
}
