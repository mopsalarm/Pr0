package com.pr0gramm.app.api.categories

import com.pr0gramm.app.NoValue
import com.pr0gramm.app.api.pr0gramm.Api
import kotlinx.coroutines.Deferred
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Query

/**
 */
interface ExtraCategoryApi {
    @GET("random")
    fun random(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int): Deferred<Api.Feed>

    @GET("controversial")
    fun controversial(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?): Deferred<Api.Feed>

    @GET("text")
    fun text(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?): Deferred<Api.Feed>

    @GET("bestof")
    fun bestof(
            @Query("tags") tags: String?,
            @Query("user") user: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?,
            @Query("score") benisScore: Int): Deferred<Api.Feed>

    @HEAD("ping")
    fun ping(): Deferred<NoValue>
}
