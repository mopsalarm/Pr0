package com.pr0gramm.app.api.meta;

import android.support.annotation.NonNull;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.pr0gramm.app.services.Graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

/**
 */
@Singleton
public class MetaService {
    private static final Logger logger = LoggerFactory.getLogger("MetaService");

    private final MetaApi api;
    private final LoadingCache<String, List<String>> userSuggestionCache;

    @Inject
    public MetaService(OkHttpClient httpClient) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new GsonAdaptersMetaApi())
                .registerTypeAdapter(byte[].class, new Base64Decoder())
                .create();

        api = new Retrofit.Builder()
                .baseUrl("https://pr0.wibbly-wobbly.de/api/meta/v1/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .validateEagerly(true)
                .build()
                .create(MetaApi.class);

        userSuggestionCache = CacheBuilder.<String, List<String>>newBuilder()
                .maximumSize(100)
                .build(CacheLoader.from(this::internalSuggestUsers));
    }

    public Observable<MetaApi.ItemsInfo> getItemsInfo(Collection<Long> items) {
        if (items.isEmpty()) {
            return Observable.just(EMPTY_INFO);
        }

        return api.info(Joiner.on(",").join(items));
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

    public List<String> suggestUsers(@NonNull String prefix) {
        return userSuggestionCache.getUnchecked(prefix);
    }

    @NonNull
    private List<String> internalSuggestUsers(@NonNull String prefix) {
        if (prefix.length() < 3)
            return emptyList();

        try {
            Response<MetaApi.Names> reponse = api.suggestUsers(prefix).execute();
            if (!reponse.isSuccessful())
                return emptyList();

            return firstNonNull(reponse.body().names(), emptyList());
        } catch (Exception error) {
            logger.warn("Could not fetch username suggestions for prefix={}: {}", prefix, error);
            return emptyList();
        }
    }

    public static class Base64Decoder implements JsonDeserializer<byte[]> {
        @Override
        public byte[] deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonNull())
                return null;

            return BaseEncoding.base64().decode(json.getAsString());
        }
    }

    private static final MetaApi.ItemsInfo EMPTY_INFO = ImmutableItemsInfo.builder().build();
}
