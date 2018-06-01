package com.pr0gramm.app.services

import android.content.SharedPreferences
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.api.pr0gramm.adapter
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.edit
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat


class SingleShotService(internal val preferences: SharedPreferences) {
    private val timeOffsetInMillis = (Math.random() * 3600.0 * 1000.0).toInt()
    private val keySetActions = "SingleShotService.actions"
    private val keyMapActions = "SingleShotService.mapActions"

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val mapAdapter = MoshiInstance.adapter<java.util.Map<String, String>>()

    private val lock = Any()

    private val timeStringMap: MutableMap<String, String> = loadTimeStringMap()

    fun isFirstTime(action: String): Boolean {
        synchronized(lock) {
            val actions = preferences.getStringSet(keySetActions, emptySet()).toMutableSet()

            if (actions.add(action)) {
                preferences.edit {
                    putStringSet(keySetActions, actions)
                }

                return true
            } else {
                return false
            }
        }
    }

    fun firstTimeInVersion(action: String): Boolean {
        val version = AndroidUtility.buildVersionCode()
        return isFirstTime("$action--$version")
    }

    fun firstTimeToday(action: String): Boolean {
        return firstTimeByTimePattern(action, "YYYY-MM-dd")
    }

    fun firstTimeInHour(action: String): Boolean {
        return firstTimeByTimePattern(action, "YYYY-MM-dd:HH")
    }

    private fun firstTimeByTimePattern(action: String, pattern: String): Boolean {
        val timeString = DateTime.now()
                .minusMillis(timeOffsetInMillis)
                .toString(DateTimeFormat.forPattern(pattern))

        return timeStringHasChanged(action, timeString)
    }

    private fun timeStringHasChanged(action: String, timeString: String): Boolean {
        synchronized(lock) {
            if (timeStringMap[action] == timeString) {
                return false
            } else {
                timeStringMap[action] = timeString

                preferences.edit {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
                    putString(keyMapActions, mapAdapter.toJson(timeStringMap as java.util.Map<String, String>))
                }

                return true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadTimeStringMap(): MutableMap<String, String> {
        try {
            val map = mapAdapter.fromJson(preferences.getString(keyMapActions, "{}")) as? Map<String, String>

            @Suppress("UNCHECKED_CAST")
            return (map ?: mapOf()).toMutableMap()

        } catch (ignored: RuntimeException) {
            return HashMap()
        }
    }
}
