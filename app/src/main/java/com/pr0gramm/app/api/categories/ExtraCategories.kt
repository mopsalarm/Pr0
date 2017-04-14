package com.pr0gramm.app.api.categories

import com.google.gson.Gson
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.subscribeOnBackground
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.Observable.just
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class ExtraCategories @Inject constructor(
        private val configService: ConfigService,
        httpClient: OkHttpClient, gson: Gson) {

    val api: ExtraCategoryApi = Retrofit.Builder()
            .client(httpClient)
            .baseUrl("https://pr0.wibbly-wobbly.de/api/categories/v1/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .build()
            .create(ExtraCategoryApi::class.java)

    private val cachedState = BehaviorSubject.create<Boolean>(false).toSerialized()
    private val updateState = PublishSubject.create<Boolean>()

    init {
        val byConfig = configService.observeConfig().map { it.getExtraCategories() }
        Observable.merge(updateState, byConfig)
                .debounce(1, TimeUnit.SECONDS, BackgroundScheduler.instance())
                .switchMap { pingOnce() }
                .distinctUntilChanged()
                .subscribe(cachedState)
    }

    val categoriesAvailable: Observable<Boolean> get() {
        updateState.onNext(true)
        return cachedState;
    }

    private fun pingOnce(): Observable<Boolean>? {
        if (configService.config().getExtraCategories()) {
            return api.ping().subscribeOnBackground()
                    .map { true }
                    .onErrorReturn { false }
        } else {
            return just(false)
        }
    }
}
