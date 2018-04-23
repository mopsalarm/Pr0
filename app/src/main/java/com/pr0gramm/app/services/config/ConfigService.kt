package com.pr0gramm.app.services.config

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import com.google.gson.Gson
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.util.ignoreException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit


/**
 * Simple config service to do remove configuration with local fallback
 */
class ConfigService(context: Application,
                    private val okHttpClient: OkHttpClient,
                    private val gson: Gson,
                    private val preferences: SharedPreferences) {

    private val settings = com.pr0gramm.app.Settings.get()

    @Volatile
    private var configState: Config = ImmutableConfig.builder().build()
    private val configSubject = BehaviorSubject.create<Config>(configState).toSerialized()

    // We are using a device hash so we can return the same config if
    // the devices asks multiple times. We do this so that we can always derive the same
    // config from the hash without storing anything on the server side.
    private val deviceHash: String

    init {
        this.deviceHash = makeUniqueIdentifier(context, preferences)

        val jsonCoded = preferences.getString(PREF_DATA_KEY, "{}")
        this.configState = loadState(gson, jsonCoded)

        publishState()

        // schedule updates once an hour
        Observable.interval(0, 1, TimeUnit.HOURS, Schedulers.io()).subscribe {
            ignoreException {
                update()
            }
        }
    }

    private fun update() {
        val url = Uri.parse("https://pr0.wibbly-wobbly.de/app-config/v2/").buildUpon()
                .appendEncodedPath("version").appendPath(BuildConfig.VERSION_CODE.toString())
                .appendEncodedPath("hash").appendPath(deviceHash)
                .appendEncodedPath("config.json")
                .appendQueryParameter("beta", settings.useBetaChannel.toString())
                .build()

        try {
            val request = Request.Builder().url(url.toString()).build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body()?.string()?.let { body ->
                        updateConfigState(body)
                    }

                } else {
                    logger.warn("Could not update app-config, response code {}", response.code())
                }
            }

        } catch (err: Exception) {
            logger.warn("Could not update app-config", err)
        }
    }

    private fun updateConfigState(body: String) {
        configState = loadState(gson, body)
        persistConfigState()
        publishState()
    }

    private fun persistConfigState() {
        logger.info("Persisting current config state")
        try {
            val jsonCoded = gson.toJson(configState)
            preferences.edit()
                    .putString(PREF_DATA_KEY, jsonCoded)
                    .apply()

        } catch (err: Exception) {
            logger.warn("Could not persist config state", err)
        }

    }

    private fun publishState() {
        logger.info("Publishing change in config state")
        try {
            configSubject.onNext(config())
        } catch (err: Exception) {
            logger.warn("Could not publish the current state Oo", err)
        }

    }

    /**
     * Observes the config. The config changes are not observed on any particual thread.
     */
    fun observeConfig(): Observable<Config> {
        return configSubject
    }

    fun config(): Config {
        if (BuildConfig.DEBUG) {
            // add debug overlays
            return ImmutableConfig.copyOf(configState)
                    .withTrackItemView(true)
                    .withAdType(Config.AdType.FEED)
        }

        return configState
    }

    companion object {
        private val logger = LoggerFactory.getLogger("ConfigService")
        private val PREF_DATA_KEY = "ConfigService.data"
        private val PREF_ID_KEY = "ConfigService.id"

        fun makeUniqueIdentifier(context: Context, preferences: SharedPreferences): String {
            // get a cached version
            var cached: String = preferences.getString(PREF_ID_KEY, null) ?: ""

            if (invalidUniqueIdentifier(cached)) {
                // try the device id.
                val resolver = context.applicationContext.contentResolver
                cached = getAndroidId(resolver) ?: ""

                // still nothing? create a random id.
                if (invalidUniqueIdentifier(cached)) {
                    cached = randomIdentifier()
                }

                // now cache the new id
                logger.info("Caching new device id.")
                preferences.edit()
                        .putString(PREF_ID_KEY, cached)
                        .apply()
            }

            return cached
        }

        private fun getAndroidId(resolver: ContentResolver?): String? = try {
            Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)
        } catch (err: Exception) {
            null
        }

        private fun loadState(gson: Gson, jsonCoded: String): Config {
            try {
                return gson.fromJson(jsonCoded, Config::class.java)
            } catch (err: Exception) {
                logger.warn("Could not decode state", err)
                return ImmutableConfig.builder().build()
            }

        }

        private fun invalidUniqueIdentifier(cached: String?): Boolean {
            if (cached == null || cached.isBlank() || cached.length < 5 || cached == "DEFACE" || cached == "UNKNOWN") {
                return true
            }

            if ("123456789".startsWith(cached)) {
                return true
            }

            return false
        }

        private fun randomIdentifier(): String {
            val length = 16
            val alphabet = "0123456789abcdef"

            val b = StringBuilder(length)
            for (i in 0 until length) {
                val r = (Math.random() * alphabet.length).toInt()
                b.append(alphabet[r])
            }

            return b.toString()
        }
    }
}
