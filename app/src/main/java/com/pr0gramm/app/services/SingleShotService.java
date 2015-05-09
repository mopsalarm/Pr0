package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.Pr0grammApplication;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 */
@Singleton
public class SingleShotService {
    private static final String KEY_ACTIONS = "SingleShotService.actions";

    private SharedPreferences preferences;

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
}
