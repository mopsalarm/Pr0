package com.pr0gramm.app.api.meta;

import android.graphics.PointF;
import android.support.annotation.Keep;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.services.Graph;
import com.squareup.okhttp.OkHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;
import rx.Observable;

/**
 */
@Singleton
public class MetaService {
    private static final Logger logger = LoggerFactory.getLogger(MetaService.class);
    private final Api api;

    @Inject
    public MetaService(OkHttpClient httpClient) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new GsonAdaptersItemsInfo())
                .registerTypeAdapterFactory(new GsonAdaptersSizeInfo())
                .registerTypeAdapterFactory(new GsonAdaptersUserInfo())
                .create();

        api = new Retrofit.Builder()
                .baseUrl("http://pr0.wibbly-wobbly.de/api/meta/v1/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build()
                .create(Api.class);
    }

    public Observable<ItemsInfo> getItemsInfo(Collection<Long> items) {
        if (items.isEmpty()) {
            return Observable.just(EMPTY_INFO);
        }

        return api.info(Joiner.on(",").join(items));
    }

    public Observable<UserInfo> getUserInfo(String username) {
        return api.user(username);
    }

    public Observable<Graph> getBenisHistory(String username) {
        return getUserInfo(username)
                .filter(info -> info.getBenisHistory().size() > 10)
                .map(info -> {
                    List<UserInfo.BenisHistoryEntry> history = Ordering.natural()
                            .onResultOf(UserInfo.BenisHistoryEntry::getTimestamp)
                            .sortedCopy(info.getBenisHistory());

                    long offset = history.get(0).getTimestamp();
                    ImmutableList<PointF> points = FluentIterable.from(history)
                            .transform(e -> new PointF(e.getTimestamp() - offset, e.getBenis()))
                            .toList();

                    long now = System.currentTimeMillis() / 1000;
                    return new Graph(0, now - offset, points);
                });
    }

    private interface Api {
        @FormUrlEncoded
        @POST("items")
        Observable<ItemsInfo> info(@Field("ids") String itemIds);

        @GET("user/{name}")
        Observable<UserInfo> user(@Path("name") String name);
    }

    private static final ItemsInfo EMPTY_INFO = ImmutableItemsInfo.builder().build();
}
