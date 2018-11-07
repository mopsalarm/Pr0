package com.pr0gramm.app.services

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.NoValue
import com.pr0gramm.app.Settings
import com.pr0gramm.app.util.logger
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import proguard.annotation.KeepPublicClassMemberNames
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


class SettingsTrackerService(httpClient: OkHttpClient) {
    private val logger = logger("SettingsTrackerService")

    private val settings: Settings = Settings.get()

    private val httpInterface: HttpInterface = Retrofit.Builder()
            .baseUrl("https://pr0.wibbly-wobbly.de/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .validateEagerly(true)
            .build()
            .create(HttpInterface::class.java)

    suspend fun track() {
        val values = settings.raw().all.filterKeys { it.startsWith("pref_") }

        httpInterface.track(mapOf("settings" to values)).await()
        logger.info { "Tracked settings successfully." }
    }

    @KeepPublicClassMemberNames
    private interface HttpInterface {
        @POST("track-settings")
        fun track(@Body values: Any): Deferred<NoValue>
    }
}
