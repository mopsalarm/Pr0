package com.pr0gramm.app.services

import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.ServiceBaseURL
import com.pr0gramm.app.model.kv.GetResult
import com.pr0gramm.app.model.kv.PutResult
import com.pr0gramm.app.ui.base.retryUpTo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.*

class KVService(okHttpClient: OkHttpClient) {
    private val api = Retrofit.Builder()
            .validateEagerly(BuildConfig.DEBUG)
            .client(okHttpClient)
            .baseUrl("$ServiceBaseURL/api/kv/v1/")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .build().create<Api>()

    suspend fun update(ident: String, key: String, fn: (previous: ByteArray?) -> ByteArray?): PutResult.Version? {
        return retryUpTo(3) {
            val previous = get(ident, key) as? GetResult.Value

            val response = fn(previous?.value)
                    ?.let { value -> put(ident, key, previous?.version ?: 0, value) }

            if (response == PutResult.Conflict) {
                throw VersionConflictException()
            }

            response as? PutResult.Version
        }
    }

    suspend fun get(ident: String, key: String): GetResult {
        return try {
            api.getValueAsync(tokenOf(ident), key)
        } catch (err: HttpException) {
            if (err.code() != 404)
                throw err

            GetResult.NoValue
        }
    }

    suspend fun put(ident: String, key: String, version: Int, value: ByteArray): PutResult {
        // hash the ident to generate the token

        val body = value.toRequestBody("application/octet".toMediaTypeOrNull())

        return try {
            api.putValueAsync(tokenOf(ident), key, version, body)
        } catch (err: HttpException) {
            if (err.code() != 409)
                throw err

            PutResult.Conflict
        }
    }

    private fun tokenOf(ident: String) = UUID.nameUUIDFromBytes("xxx$ident".toByteArray())

    private interface Api {
        @GET("token/{token}/key/{key}")
        suspend fun getValueAsync(
                @Path("token") token: UUID,
                @Path("key") key: String): GetResult.Value

        @POST("token/{token}/key/{key}/version/{version}")
        suspend fun putValueAsync(
                @Path("token") token: UUID,
                @Path("key") key: String,
                @Path("version") version: Int,
                @Body body: RequestBody): PutResult.Version
    }

    class VersionConflictException : RuntimeException("version conflict during update")
}