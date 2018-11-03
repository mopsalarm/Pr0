package com.pr0gramm.app.api.categories

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.base.toObservable
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.logger
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import rx.Observable
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 */
class ExtraCategories(private val configService: ConfigService, httpClient: OkHttpClient) {
    val logger = logger("ExtraCategories")

    val api: ExtraCategoryApi = Retrofit.Builder()
            .client(httpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/api/categories/v1/")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
            .create(ExtraCategoryApi::class.java)

    private val cachedState = AtomicBoolean()

    val categoriesAvailable: Observable<Boolean> get() {
        return configService.observeConfig()
                .observeOn(BackgroundScheduler)

                .switchMap { config ->
                    if (config.extraCategories) {
                        Observable
                                .interval(0, 1, TimeUnit.MINUTES, Schedulers.io())
                                .flatMap { toObservable { pingOnce() } }
                    } else {
                        Observable.just(false)
                    }
                }

                // always emit the last known state first
                .doOnNext { cachedState.set(it) }
                .startWith(Observable.fromCallable { cachedState.get() })

                .distinctUntilChanged()
    }

    private suspend fun pingOnce(): Boolean {
        return try {
            api.ping().await()
            true
        } catch (err: Exception) {
            logger.warn { "ping failed: $err" }
            false
        }
    }
}
