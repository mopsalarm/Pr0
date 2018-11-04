package com.pr0gramm.app.api.categories

import com.pr0gramm.app.api.pr0gramm.Api
import kotlinx.coroutines.Deferred
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Query

/**
 */
interface ExtraCategoryApi {
    @GET("text")
    fun text(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?): Deferred<Api.Feed>

    @HEAD("ping")
    fun ping(): Deferred<Response<Void>>
}
