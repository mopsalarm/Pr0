package com.pr0gramm.app.api.meta;

import android.support.annotation.NonNull;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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

import retrofit.Call;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;
import rx.Observable;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

/**
 */
@Singleton
public class MetaService {
    private static final Logger logger = LoggerFactory.getLogger("MetaService");

    private final Api api;
    private final LoadingCache<String, List<String>> userSuggestionCache;

    @Inject
    public MetaService(OkHttpClient httpClient) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new GsonAdaptersItemsInfo())
                .registerTypeAdapterFactory(new GsonAdaptersSizeInfo())
                .registerTypeAdapterFactory(new GsonAdaptersUserInfo())
                .registerTypeAdapterFactory(new GsonAdaptersNames())
                .create();

        api = new Retrofit.Builder()
                .baseUrl("https://pr0.wibbly-wobbly.de/api/meta/v1/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .validateEagerly()
                .build()
                .create(Api.class);

        userSuggestionCache = CacheBuilder.<String, List<String>>newBuilder()
                .maximumSize(100)
                .build(CacheLoader.from(this::internalSuggestUsers));
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

                    ImmutableList<Graph.Point> points = FluentIterable.from(history)
                            .transform(e -> new Graph.Point(e.getTimestamp(), e.getBenis()))
                            .toList();

                    long now = System.currentTimeMillis() / 1000;
                    return new Graph(points.get(0).x, now, points);
                });
    }

    public List<String> suggestUsers(@NonNull String prefix) {
        return userSuggestionCache.getUnchecked(prefix);
    }

    @NonNull
    private List<String> internalSuggestUsers(@NonNull String prefix) {
        if (prefix.length() < 3)
            return emptyList();

        try {
            Response<Names> reponse = api.suggestUsers(prefix).execute();
            if (!reponse.isSuccess())
                return emptyList();

            return firstNonNull(reponse.body().names(), emptyList());
        } catch (Exception error) {
            logger.warn("Could not fetch username suggestions for prefix={}: {}", prefix, error);
            return emptyList();
        }
    }

    private interface Api {
        @FormUrlEncoded
        @POST("items")
        Observable<ItemsInfo> info(@Field("ids") String itemIds);

        @GET("user/{name}")
        Observable<UserInfo> user(@Path("name") String name);

        @GET("user/suggest/{prefix}")
        Call<Names> suggestUsers(@Path("prefix") String prefix);
    }

    private static final ItemsInfo EMPTY_INFO = ImmutableItemsInfo.builder().build();
}
