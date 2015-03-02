package com.pr0gramm.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 */
public class SettingsActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout view = new FrameLayout(this);
        view.setId(android.R.id.content);
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(view);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);
            updateContentTypeBoxes(getPreferenceManager().getSharedPreferences());
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen()
                    .getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            getPreferenceScreen()
                    .getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);

            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            updateContentTypeBoxes(preferences);
        }

        private void updateContentTypeBoxes(SharedPreferences sharedPreferences) {
            Settings prefs = Settings.of(sharedPreferences);
            boolean enabled = prefs.getContentType().size() > 1;
            List<String> contentTypeKeys = ImmutableList.of(
                    "pref_feed_type_sfw", "pref_feed_type_nsfw", "pref_feed_type_nsfl");

            for (String ctKey : contentTypeKeys) {
                Preference pref = findPreference(ctKey);

                if (sharedPreferences.getBoolean(ctKey, false))
                    pref.setEnabled(enabled);
            }
        }
    }

}
