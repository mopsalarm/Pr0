package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.adapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.getStringOrNull
import java.text.SimpleDateFormat
import java.util.*


class SingleShotService(private val preferences: SharedPreferences) {
    private val timeOffset = Duration.millis((Math.random() * 3600.0 * 1000.0).toLong())

    private val keySetActions = "SingleShotService.actions"
    private val keyMapActions = "SingleShotService.mapActions"

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val mapAdapter = MoshiInstance.adapter<HashMap<String, String>>()

    private val lock = Any()

    private val timeStringMap: MutableMap<String, String> = loadTimeStringMap()

    /**
     * Marks the given action as done and returns true, if it was not yet done.
     */
    fun markAsDoneOnce(action: String): Boolean {
        synchronized(lock) {
            val actions = (preferences.getStringSet(keySetActions, null) ?: setOf()).toMutableSet()

            return if (actions.add(action)) {
                preferences.edit {
                    putStringSet(keySetActions, actions)
                }

                true
            } else {
                false
            }
        }
    }

    inline fun doOnce(action: String, block: () -> Unit) {
        if (markAsDoneOnce(action)) {
            block()
        }
    }

    fun firstTimeInVersion(action: String): Boolean {
        val version = AndroidUtility.buildVersionCode()
        return markAsDoneOnce("$action--$version")
    }

    fun firstTimeToday(action: String): Boolean {
        return firstTimeByTimePattern(action, "yyyy-MM-dd")
    }

    fun firstTimeInHour(action: String): Boolean {
        return firstTimeByTimePattern(action, "yyyy-MM-dd:HH")
    }

    private fun firstTimeByTimePattern(action: String, pattern: String): Boolean {
        return timeStringHasChanged(action, Instant.now()
                .minus(timeOffset)
                .toString(SimpleDateFormat(pattern, Locale.ROOT)))
    }

    private fun timeStringHasChanged(action: String, timeString: String): Boolean {
        synchronized(lock) {
            return if (timeStringMap[action] == timeString) {
                false
            } else {
                timeStringMap[action] = timeString

                val copy = HashMap<String, String>()
                copy.putAll(timeStringMap)

                preferences.edit {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
                    putString(keyMapActions, mapAdapter.toJson(copy))
                }

                true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadTimeStringMap(): MutableMap<String, String> {
        try {
            return mapAdapter.fromJson(preferences
                    .getStringOrNull(keyMapActions) ?: "{}") ?: HashMap()

        } catch (ignored: RuntimeException) {
            return HashMap()
        }
    }
}
