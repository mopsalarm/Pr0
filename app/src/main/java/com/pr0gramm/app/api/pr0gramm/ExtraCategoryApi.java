package com.pr0gramm.app.api.pr0gramm;

import com.pr0gramm.app.api.pr0gramm.response.Feed;

import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 */
public interface ExtraCategoryApi {
    @GET("/random")
    Observable<Feed> random(@Query("flags") int flags);

    @GET("/controversial")
    Observable<Feed> controversial(@Query("flags") int flags, @Query("older") Long older);
}
