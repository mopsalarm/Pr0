package com.pr0gramm.app.api.categories

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 */
@Singleton
class ExtraCategoryApiProvider @Inject constructor(httpClient: OkHttpClient, gson: Gson) : Provider<ExtraCategoryApi> {
    private val api = Retrofit.Builder()
            .client(httpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/api/categories/v1/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .build()
            .create(ExtraCategoryApi::class.java)

    override fun get(): ExtraCategoryApi {
        return api
    }
}
