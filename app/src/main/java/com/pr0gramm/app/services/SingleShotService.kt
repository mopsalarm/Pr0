package com.pr0gramm.app.services

import android.content.SharedPreferences
import com.google.gson.Gson
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.edit
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.*


/**
 */

class SingleShotService(internal val preferences: SharedPreferences) {
    private val lock = Any()
    private val gson = Gson()
    private var timeStringMap: MutableMap<String, String> = loadTimeStringMap()

    fun isFirstTime(action: String): Boolean {
        synchronized(lock) {
            val actions = preferences.getStringSet(KEY_ACTIONS, emptySet()).toMutableSet()

            if (actions.add(action)) {
                preferences.edit() {
                    putStringSet(KEY_ACTIONS, actions)
                }

                return true
            } else {
                return false
            }
        }
    }

    fun firstTimeInVersion(action: String): Boolean {
        val version = AndroidUtility.buildVersionCode()
        return isFirstTime(action + "--" + version)
    }

    fun firstTimeToday(action: String): Boolean {
        return firstTimeByTimePattern(action, "YYYY-MM-dd")
    }

    fun firstTimeInHour(action: String): Boolean {
        return firstTimeByTimePattern(action, "YYYY-MM-dd:HH")
    }

    private fun firstTimeByTimePattern(action: String, pattern: String): Boolean {
        val timeString = DateTime.now()
                .minusMillis(TIME_OFFSET_IN_MILLIS)
                .toString(DateTimeFormat.forPattern(pattern))

        return timeStringHasChanged(action, timeString)
    }

    private fun timeStringHasChanged(action: String, timeString: String): Boolean {
        synchronized(lock) {
            if (timeStringMap[action] == timeString) {
                return false
            } else {
                timeStringMap.put(action, timeString)

                preferences.edit() {
                    putString(KEY_MAP_ACTIONS, gson.toJson(timeStringMap))
                }

                return true
            }
        }
    }

    private fun loadTimeStringMap(): MutableMap<String, String> {
        try {
            @Suppress("UNCHECKED_CAST")
            return HashMap(gson.fromJson(
                    preferences.getString(KEY_MAP_ACTIONS, "{}"),
                    Map::class.java)) as MutableMap<String, String>

        } catch (ignored: RuntimeException) {
            return HashMap<String, String>()
        }
    }

    companion object {
        internal val TIME_OFFSET_IN_MILLIS = (Math.random() * 3600.0 * 1000.0).toInt()

        private val KEY_ACTIONS = "SingleShotService.actions"
        private val KEY_MAP_ACTIONS = "SingleShotService.mapActions"
    }
}
