package com.pr0gramm.app.services.config

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.adapter
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.*
import org.kodein.di.erased.instance
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit


/**
 * Simple config service to do remove configuration with local fallback
 */
class ConfigService(context: Application,
                    private val api: Api,
                    private val preferences: SharedPreferences) {

    private val settings = com.pr0gramm.app.Settings.get()

    @Volatile
    private var configState: Config = Config()

    private val configSubject = BehaviorSubject.create<Config>(configState).toSerialized()

    // We are using a device hash so we can return the same config if
    // the devices asks multiple times. We do this so that we can always derive the same
    // config from the hash without storing anything on the server side.
    private val deviceHash = makeUniqueIdentifier(context, preferences)

    init {
        val jsonCoded = preferences.getString(PREF_DATA_KEY) ?: "{}"
        this.configState = loadState(jsonCoded)

        publishState()

        // schedule updates once an hour
        Observable.interval(0, 1, TimeUnit.HOURS, BackgroundScheduler).subscribe {
            update()
        }
    }

    private fun update() {
        api.remoteConfig(BuildConfig.VERSION_CODE, deviceHash, settings.useBetaChannel)
                .subscribeOnBackground()
                .doOnNext { logger.info { "Fetching remote config was successful" } }
                .onErrorReturn { err ->
                    logger.warn(err) { "Error fetching current remote config" }
                    Config()
                }
                .subscribe { updateConfigState(it) }
    }

    private fun updateConfigState(config: Config) {
        configState = config
        persistConfigState()
        publishState()
    }

    private fun persistConfigState() {
        logger.info { "Persisting current config state" }
        try {
            val jsonCoded = MoshiInstance.adapter<Config>().toJson(configState)
            preferences.edit {
                putString(PREF_DATA_KEY, jsonCoded)
            }
        } catch (err: Exception) {
            logger.warn("Could not persist config state", err)
        }
    }

    private fun publishState() {
        logger.info { "Publishing change in config state" }
        try {
            configSubject.onNext(config())
        } catch (err: Throwable) {
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
        debug {
            // update config for development.
            return configState.copy(trackItemView = true, adType = Config.AdType.FEED,
                    specialMenuItems = listOf(Config.MenuItem(
                            name = "Wichteln",
                            icon = "https://materialdesignicons.com/api/download/22D0C782-CD05-4FEB-845F-BBA7126C7326/000000/1/FFFFFF/0/48",
                            link = "https://pr0gramm.com/new/wichteln",
                            lower = false,
                            requireLogin = true)))
        }

        return configState
    }

    companion object {
        private val logger = logger("ConfigService")
        private const val PREF_DATA_KEY = "ConfigService.data"
        private const val PREF_ID_KEY = "ConfigService.id"

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
                logger.info { "Caching new device id." }
                preferences.edit()
                        .putString(PREF_ID_KEY, cached)
                        .apply()
            }

            return cached
        }

        private fun getAndroidId(resolver: ContentResolver?): String? = try {
            Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)
        } catch (_: Throwable) {
            null
        }

        private fun loadState(jsonCoded: String): Config {
            return try {
                MoshiInstance.adapter<Config>().fromJson(jsonCoded) ?: Config()
            } catch (err: Exception) {
                logger.warn("Could not decode state", err)
                Config()
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

        fun get(context: Context): Config {
            return context.directKodein.instance()
        }
    }
}
