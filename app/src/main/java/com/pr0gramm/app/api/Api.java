package com.pr0gramm.app.api;

import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 */
public interface Api {
    @GET("/api/items/get")
    Observable<Feed> itemsGetOlder(
            @Query("promoted") int promoted,
            @Query("older") long older,
            @Query("flags") int flags,
            @Query("tags") String tags);

    @GET("/api/items/get")
    Observable<Feed> itemsGetNewer(
            @Query("promoted") int promoted,
            @Query("newer") long newer,
            @Query("flags") int flags,
            @Query("tags") String tags);

    @GET("/api/items/info")
    Observable<Post> info(@Query("itemId") long itemId);
}
