package com.pr0gramm.app.api.categories;

import com.pr0gramm.app.api.pr0gramm.response.Feed;
import com.squareup.okhttp.ResponseBody;

import retrofit.Response;
import retrofit.http.GET;
import retrofit.http.HEAD;
import retrofit.http.Query;
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

    @HEAD("ping")
    Observable<Response<ResponseBody>> ping();
}
