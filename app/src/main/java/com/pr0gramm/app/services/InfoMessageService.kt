package com.pr0gramm.app.services

import com.pr0gramm.app.MoshiInstance
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import rx.Observable


/**
 * Gets a short info message. This might be used to inform about
 * server failures.
 */

class InfoMessageService(okHttpClient: OkHttpClient) {
    private val api = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .validateEagerly(true)
            .build()
            .create(Api::class.java)

    /**
     * Returns an observable that might produce a message, if one is available.
     */
    fun fetch(): Observable<Message> {
        return api.get()
    }

    private interface Api {
        @GET("info-message.json")
        fun get(): Observable<Message>
    }

    @JsonClass(generateAdapter = true)
    class Message(val message: String? = null, val endOfLife: Int = 0)
}
