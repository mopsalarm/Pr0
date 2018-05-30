package com.pr0gramm.app.api.categories

import com.pr0gramm.app.api.pr0gramm.Api
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Query
import rx.Observable

/**
 */
interface ExtraCategoryApi {
    @GET("random")
    fun random(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int): Observable<Api.Feed>

    @GET("controversial")
    fun controversial(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?): Observable<Api.Feed>

    @GET("text")
    fun text(
            @Query("tags") tags: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?): Observable<Api.Feed>

    @GET("bestof")
    fun bestof(
            @Query("tags") tags: String?,
            @Query("user") user: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?,
            @Query("score") benisScore: Int): Observable<Api.Feed>

    @GET("general")
    fun general(
            @Query("promoted") promoted: Int?,
            @Query("tags") tags: String?,
            @Query("user") user: String?,
            @Query("flags") flags: Int,
            @Query("older") older: Long?,
            @Query("newer") newer: Long?,
            @Query("around") around: Long?): Observable<Api.Feed>

    @HEAD("ping")
    fun ping(): Observable<Void>
}
