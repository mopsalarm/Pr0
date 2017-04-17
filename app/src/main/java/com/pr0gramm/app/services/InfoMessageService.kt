package com.pr0gramm.app.services

import android.support.annotation.Keep
import okhttp3.OkHttpClient
import proguard.annotation.KeepClassMembers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
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
            .addConverterFactory(GsonConverterFactory.create())
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

    @Keep
    @KeepClassMembers
    class Message {
        var message: String? = null
        var endOfLife: Int = 0
    }
}
