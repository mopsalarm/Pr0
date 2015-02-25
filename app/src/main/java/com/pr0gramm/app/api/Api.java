package com.pr0gramm.app.api;

import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 */
public interface Api {
    @GET("/api/items/get")
    Observable<Feed> itemsGet(
            @Query("promoted") int promoted,
            @Query("older") int older,
            @Query("flags") int flags);
}
