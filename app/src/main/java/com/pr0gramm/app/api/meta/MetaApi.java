package com.pr0gramm.app.api.meta;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import rx.Observable;

/**
 * The meta api interface with types.
 */
@org.immutables.gson.Gson.TypeAdapters
public interface MetaApi {
    @FormUrlEncoded
    @POST("items")
    Observable<ItemsInfo> info(@Field("ids") String itemIds);

    @GET("user/{name}")
    Observable<UserInfo> user(@Path("name") String name);

    @GET("user/suggest/{prefix}")
    Call<Names> suggestUsers(@Path("prefix") String prefix);

    @Value.Immutable
    interface ItemsInfo {
        List<Long> getReposts();
    }

    @Value.Immutable
    @Gson.TypeAdapters
    interface Names {
        List<String> names();
    }

    @Value.Immutable
    interface SizeInfo {
        long getId();

        int getWidth();

        int getHeight();
    }

    @Value.Immutable
    interface UserInfo {
        List<BenisHistoryEntry> getBenisHistory();
    }

    @Value.Immutable(builder = false)
    interface BenisHistoryEntry {
        @Value.Parameter(order = 0)
        long getTimestamp();

        @Value.Parameter(order = 1)
        int getBenis();
    }

    @Value.Immutable
    interface PreviewInfo {
        long id();

        int width();

        int height();

        String pixels();
    }
}
