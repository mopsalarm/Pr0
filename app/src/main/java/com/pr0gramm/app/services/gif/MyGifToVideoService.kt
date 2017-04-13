package com.pr0gramm.app.services.gif

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import okhttp3.OkHttpClient
import proguard.annotation.Keep
import proguard.annotation.KeepClassMembers
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import rx.Observable

/**
 * Converts a gif to a webm using my own conversion service.
 */
class MyGifToVideoService(httpClient: OkHttpClient) : GifToVideoService {

    private val api = Retrofit.Builder()
            .baseUrl(DEFAULT_ENDPOINT)
            .client(httpClient)
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)

    /**
     * Converts this gif into a webm, if possible.

     * @param gifUrl The url of the gif to convert.
     * *
     * @return A observable producing exactly one item with the result.
     */
    override fun toVideo(gifUrl: String): Observable<GifToVideoService.Result> {
        val fallback = Observable.just(GifToVideoService.Result(gifUrl))
        if (!gifUrl.toLowerCase().endsWith(".gif")) {
            return fallback
        }

        val encoded = BaseEncoding.base64Url().encode(gifUrl.toByteArray(Charsets.UTF_8))
        return api.convert(encoded)
                .map { result -> GifToVideoService.Result(gifUrl, DEFAULT_ENDPOINT + result.path) }
                .onErrorResumeNext(fallback)
    }

    /**
     * Simple gif-to-webm service.
     */
    private interface Api {
        @GET("convert/{url}")
        fun convert(@Path("url") encodedUrl: String): Observable<ConvertResult>
    }

    /**
     */
    @Keep
    @KeepClassMembers
    private data class ConvertResult(var path: String = "")

    companion object {
        private const val DEFAULT_ENDPOINT = "https://pr0.wibbly-wobbly.de/api/gif-to-webm/v1/"
    }
}
