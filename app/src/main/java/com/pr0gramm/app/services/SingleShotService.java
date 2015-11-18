package com.pr0gramm.app.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.pr0gramm.app.util.AndroidUtility;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 */
@Singleton
public class SingleShotService {
    private static final String KEY_ACTIONS = "SingleShotService.actions";
    private static final String KEY_MAP_ACTIONS = "SingleShotService.mapActions";

    private final Gson gson = new Gson();
    private final SharedPreferences preferences;
    private final Context context;

    private Map<String, String> timeStringMap;

    private final Object lock = new Object();

    @Inject
    public SingleShotService(SharedPreferences preferences, Context context) {
        this.preferences = preferences;
        this.context = context;
    }

    public synchronized boolean isFirstTime(String action) {
        synchronized (lock) {
            Set<String> actions = new HashSet<>(preferences.getStringSet(
                    KEY_ACTIONS, Collections.<String>emptySet()));

            if (actions.add(action)) {
                // store modifications
                preferences.edit().putStringSet(KEY_ACTIONS, actions).apply();
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean firstTimeInVersion(String action) {
        int version = AndroidUtility.getPackageVersionCode(context);
        return isFirstTime(action + "--" + version);
    }

    public boolean firstTimeToday(String action) {
        return firstTimeByTimePattern(action, "YYYY-MM-dd");
    }

    public boolean firstTimeInHour(String action) {
        return firstTimeByTimePattern(action, "YYYY-MM-dd:HH");
    }

    public boolean firstTimeByTimePattern(String action, String pattern) {
        String timeString = DateTime.now().toString(DateTimeFormat.forPattern(pattern));
        return timeStringHasChanged(action, timeString);
    }

    public TestOnlySingleShotService test() {
        return new TestOnlySingleShotService();
    }

    public class TestOnlySingleShotService {
        private TestOnlySingleShotService() {
        }

        public boolean isFirstTime(String action) {
            synchronized (lock) {
                return preferences
                        .getStringSet(KEY_ACTIONS, Collections.<String>emptySet())
                        .contains(action);
            }
        }

        public boolean firstTimeInVersion(String action) {
            int version = AndroidUtility.getPackageVersionCode(context);
            return isFirstTime(action + "--" + version);
        }

        public boolean firstTimeToday(String action) {
            return firstTimeByTimePattern(action, "YYYY-MM-dd");
        }

        public boolean firstTimeInHour(String action) {
            return firstTimeByTimePattern(action, "YYYY-MM-dd:HH");
        }

        public boolean firstTimeByTimePattern(String action, String pattern) {
            String timeString = DateTime.now().toString(DateTimeFormat.forPattern(pattern));
            return timeStringHasChanged(action, timeString);
        }

        private boolean timeStringHasChanged(String action, String timeString) {
            synchronized (lock) {
                return timeStringMap == null || !timeString.equals(timeStringMap.get(action));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean timeStringHasChanged(String action, String timeString) {
        synchronized (lock) {
            if (timeStringMap == null) {
                try {
                    timeStringMap = new HashMap<>(gson.fromJson(
                            preferences.getString(KEY_MAP_ACTIONS, "{}"),
                            Map.class));

                } catch (RuntimeException ignored) {
                    timeStringMap = new HashMap<>();
                }
            }

            if (timeString.equals(timeStringMap.get(action))) {
                return false;
            } else {
                timeStringMap.put(action, timeString);

                preferences.edit()
                        .putString(KEY_MAP_ACTIONS, gson.toJson(timeStringMap))
                        .apply();

                return true;
            }
        }
    }
}
