package com.pr0gramm.app.services

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.MoshiInstance
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET


/**
 * Gets a short info message. This might be used to inform about
 * server failures.
 */

class InfoMessageService(okHttpClient: OkHttpClient) {
    private val api = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .validateEagerly(BuildConfig.DEBUG)
            .build()
            .create(Api::class.java)

    suspend fun fetch(): Message {
        return api.get().await()
    }

    private interface Api {
        @GET("info-message.json")
        fun get(): Deferred<Message>
    }

    @JsonClass(generateAdapter = true)
    class Message(val message: String? = null, val endOfLife: Int = 0)
}
