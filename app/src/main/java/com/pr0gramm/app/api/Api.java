package com.pr0gramm.app.api;

import com.pr0gramm.app.feed.Nothing;

import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;
import rx.Observable;

/**
 */
public interface Api {
    @GET("/api/items/get")
    Observable<Feed> itemsGet(
            @Query("promoted") int promoted,
            @Query("older") Long older,
            @Query("newer") Long newer,
            @Query("flags") int flags,
            @Query("tags") String tags,
            @Query("likes") String likes,
            @Query("self") String self);

    @GET("/api/items/info")
    Observable<Post> info(@Query("itemId") long itemId);

    @FormUrlEncoded
    @POST("/api/items/vote")
    Observable<Nothing> vote(@Field("id") long id, @Field("state") int state);

    @FormUrlEncoded
    @POST("/api/user/login")
    Observable<LoginResponse> login(
            @Field("name") String username,
            @Field("password") String password);


    // BAN
    // {"success":false,"ban":{"banned":true,"till":"1425325166","reason":"Regel #9 (\"webm pr0gramm app android\", \"bei den tags versagt\")"},"ts":1425245119,"cache":null,"rt":94,"qc":2}
}
