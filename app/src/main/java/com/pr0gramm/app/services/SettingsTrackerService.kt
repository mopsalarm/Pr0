package com.pr0gramm.app.services

import com.google.gson.Gson
import com.pr0gramm.app.Settings
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import proguard.annotation.KeepPublicClassMemberNames
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsTrackerService @Inject constructor(
        private val settings: Settings, httpClient: OkHttpClient, gson: Gson) {

    private val httpInterface: HttpInterface = Retrofit.Builder()
            .baseUrl("https://pr0.wibbly-wobbly.de/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .validateEagerly(true)
            .build()
            .create(HttpInterface::class.java)

    fun track() {
        val values = settings.raw().all.filterKeys { it.startsWith("pref_") }

        // track the object!
        httpInterface.track(mapOf("settings" to values)).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                logger.info("Tracked settings successfully.")
            }

            override fun onFailure(call: Call<Void>, err: Throwable) {
                logger.error("Could not track settings", err)
            }
        })
    }

    @KeepPublicClassMemberNames
    private interface HttpInterface {
        @POST("track-settings")
        fun track(@Body values: Map<String, Any>): Call<Void>
    }

    companion object {
        internal val logger = LoggerFactory.getLogger("SettingsTrackerService")
    }
}
