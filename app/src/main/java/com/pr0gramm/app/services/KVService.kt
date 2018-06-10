package com.pr0gramm.app.services

import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.util.ofType
import com.squareup.moshi.JsonClass
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import rx.Observable
import java.util.*

class KVService(okHttpClient: OkHttpClient) {
    private val api = Retrofit.Builder()
            .validateEagerly(true)
            .client(okHttpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/api/kv/v1/")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .build()
            .create(Api::class.java)

    fun update(ident: String, key: String, fn: (previous: ByteArray?) -> ByteArray?): Observable<PutResult.Version> {
        return get(ident, key)
                .flatMap { result ->
                    val empty = Observable.empty<PutResult>()

                    when (result) {
                    // no previous value, write as 0.
                        is GetResult.NoValue -> {
                            val value = fn(null) ?: return@flatMap empty
                            put(ident, key, 0, value)
                        }
                        is GetResult.Value -> {
                            val value = fn(result.value) ?: return@flatMap empty
                            put(ident, key, result.version, value)
                        }
                    }
                }
                .flatMap {
                    if (it === PutResult.Conflict) {
                        Observable.error(VersionConflictException())
                    } else {
                        Observable.just(it)
                    }
                }
                .ofType<PutResult.Version>()
                .retry(3)
    }

    fun get(ident: String, key: String): Observable<GetResult> {
        // hash the ident to generate the token
        val token = tokenOf(ident)

        return api.getValue(token, key).ofType<GetResult>().onErrorResumeNext { err ->
            if (err is HttpException && err.code() == 404) {
                return@onErrorResumeNext Observable.just(GetResult.NoValue)
            }

            Observable.error(err)
        }
    }

    fun put(ident: String, key: String, version: Int, value: ByteArray): Observable<PutResult> {
        // hash the ident to generate the token
        val token = tokenOf(ident)

        val body = RequestBody.create(MediaType.parse("application/octet"), value)
        return api.putValue(token, key, version, body).ofType<PutResult>().onErrorResumeNext { err ->
            if (err is HttpException && err.code() == 409) {
                return@onErrorResumeNext Observable.just(PutResult.Conflict)
            }

            Observable.error(err)
        }
    }

    private fun tokenOf(ident: String) = UUID.nameUUIDFromBytes("xxx$ident".toByteArray())

    private interface Api {
        @GET("token/{token}/key/{key}")
        fun getValue(
                @Path("token") token: UUID,
                @Path("key") key: String): Observable<GetResult.Value>

        @POST("token/{token}/key/{key}/version/{version}")
        fun putValue(
                @Path("token") token: UUID,
                @Path("key") key: String,
                @Path("version") version: Int,
                @Body body: RequestBody): Observable<PutResult.Version>
    }

    sealed class PutResult {
        @JsonClass(generateAdapter = true)
        class Version(val version: Int) : PutResult()

        object Conflict : PutResult()
    }

    sealed class GetResult {
        @JsonClass(generateAdapter = true)
        class Value(val version: Int, val value: ByteArray) : GetResult()

        object NoValue : GetResult()
    }

    class VersionConflictException : RuntimeException("version conflict during update")
}