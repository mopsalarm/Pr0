package com.pr0gramm.app.api.categories;

import com.pr0gramm.app.api.pr0gramm.response.Feed;

import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.Query;
import rx.Observable;

/**
 */
public interface ExtraCategoryApi {
    @GET("random")
    Observable<Feed> random(@Query("tags") String tags,
                            @Query("flags") int flags);

    @GET("controversial")
    Observable<Feed> controversial(@Query("flags") int flags,
                                   @Query("older") Long older);

    @GET("bestof")
    Observable<Feed> bestof(@Query("tags") String tags,
                            @Query("user") String user,
                            @Query("flags") int flags,
                            @Query("older") Long older,
                            @Query("score") int benisScore);

    @HEAD("ping")
    Observable<Void> ping();
}
