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

    private Map<String, String> todayMap;

    @Inject
    public SingleShotService(SharedPreferences preferences, Context context) {
        this.preferences = preferences;
        this.context = context;
    }

    public synchronized boolean isFirstTime(String action) {
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

    public boolean isFirstTimeInVersion(String action) {
        int version = AndroidUtility.getPackageVersionCode(context);
        return isFirstTime(action + "--" + version);
    }

    @SuppressWarnings("unchecked")
    public synchronized boolean isFirstTimeToday(String action) {
        String today = DateTime.now().toString(DateTimeFormat.forPattern("YYYY-MM-dd"));
        if (todayMap == null) {
            todayMap = new HashMap<>(gson.fromJson(
                    preferences.getString(KEY_MAP_ACTIONS, "{}"),
                    Map.class));
        }

        if (today.equals(todayMap.get(action))) {
            return false;
        } else {
            todayMap.put(action, today);

            preferences.edit()
                    .putString(KEY_MAP_ACTIONS, gson.toJson(todayMap))
                    .apply();

            return true;
        }
    }
}
