package com.pr0gramm.app.services

import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.ServiceBaseURL
import com.pr0gramm.app.model.info.InfoMessage
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET


/**
 * Gets a short info message. This might be used to inform about
 * server failures.
 */

class InfoMessageService(okHttpClient: OkHttpClient) {
    private val api: Api = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("$ServiceBaseURL/")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .validateEagerly(BuildConfig.DEBUG)
            .build().create()

    suspend fun fetch(): InfoMessage {
        return api.get()
    }

    private interface Api {
        @GET("info-message.json")
        suspend fun get(): InfoMessage
    }
}
