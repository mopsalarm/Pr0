package com.pr0gramm.app.api.meta;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

import java.util.List;

import retrofit.Call;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;
import rx.Observable;

/**
 * The meta api interface with types.
 */
@org.immutables.gson.Gson.TypeAdapters
public interface MetaApi {
    @FormUrlEncoded
    @POST("items?previews=true")
    Observable<ItemsInfo> info(@Field("ids") String itemIds);

    @GET("user/{name}")
    Observable<UserInfo> user(@Path("name") String name);

    @GET("user/suggest/{prefix}")
    Call<Names> suggestUsers(@Path("prefix") String prefix);

    @Value.Immutable
    interface ItemsInfo {
        List<Long> getReposts();

        List<SizeInfo> getSizes();

        List<PreviewInfo> getPreviews();
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
