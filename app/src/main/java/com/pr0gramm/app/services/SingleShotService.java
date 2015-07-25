package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.Pr0grammApplication;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 */
@Singleton
public class SingleShotService {
    private static final String KEY_ACTIONS = "SingleShotService.actions";
    private static final String KEY_MAP_ACTIONS = "SingleShotService.mapActions";

    private final Gson gson = new Gson();
    private final SharedPreferences preferences;

    @Inject
    public SingleShotService(SharedPreferences preferences) {
        this.preferences = preferences;
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
        int version = Pr0grammApplication.getPackageInfo().versionCode;
        return isFirstTime(action + "--" + version);
    }

    @SuppressWarnings("unchecked")
    public synchronized boolean isFirstTimeToday(String action) {
        String today = DateTime.now().toString(DateTimeFormat.forPattern("YYYY-MM-dd"));
        Map<String, String> map = gson.fromJson(preferences.getString(KEY_MAP_ACTIONS, "{}"), Map.class);

        if (today.equals(map.get(action))) {
            return false;
        } else {
            map = new HashMap<>(map);
            map.put(action, today);

            preferences.edit()
                    .putString(KEY_MAP_ACTIONS, gson.toJson(map))
                    .apply();

            return true;
        }
    }
}
