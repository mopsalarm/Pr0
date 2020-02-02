package com.pr0gramm.app.services

import android.os.Build
import com.pr0gramm.app.*
import com.pr0gramm.app.util.getStringOrNull
import okhttp3.OkHttpClient
import proguard.annotation.KeepPublicClassMemberNames
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST


class SettingsTrackerService(httpClient: OkHttpClient) {
    private val logger = Logger("SettingsTrackerService")

    private val settings: Settings = Settings.get()

    private val httpInterface: HttpInterface = Retrofit.Builder()
            .baseUrl("$ServiceBaseURL/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .validateEagerly(BuildConfig.DEBUG)
            .build()
            .create(HttpInterface::class.java)

    suspend fun track() {
        val values = settings.raw().all.filterKeys { it.startsWith("pref_") }

        val payload: Map<String, Any?> = mapOf(
                "_id" to settings.raw().getStringOrNull("__unique_settings_id"),
                "version" to BuildConfig.VERSION_CODE,
                "abis" to Build.SUPPORTED_ABIS.joinToString(","),
                "settings" to values)

        httpInterface.track(payload)

        logger.debug { "Tracked settings successfully." }
    }

    @KeepPublicClassMemberNames
    private interface HttpInterface {
        @POST("track-settings")
        suspend fun track(@Body values: Any)
    }
}
