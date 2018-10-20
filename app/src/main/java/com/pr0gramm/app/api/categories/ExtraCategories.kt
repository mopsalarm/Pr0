package com.pr0gramm.app.api.categories

import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.subscribeOnBackground
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
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
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .build()
            .create(ExtraCategoryApi::class.java)

    private val cachedState = AtomicBoolean()

    val categoriesAvailable: Observable<Boolean> get() {
        return configService.observeConfig()
                .switchMap { config ->
                    if (config.extraCategories) {
                        Observable.interval(0, 1, TimeUnit.MINUTES, Schedulers.io()).flatMap { pingOnce() }
                    } else {
                        Observable.just(false)
                    }
                }

                // always emit the last known state first
                .doOnNext { cachedState.set(it) }
                .startWith(Observable.fromCallable { cachedState.get() })

                .distinctUntilChanged()
    }

    private fun pingOnce(): Observable<Boolean>? {
        return api.ping()
                .subscribeOnBackground()
                .map { true }
                .onErrorReturn { err ->
                    logger.warn("ping failed", err)
                    false
                }
    }
}
