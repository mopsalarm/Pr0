package com.pr0gramm.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.LogcatUtility;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import roboguice.util.RoboContext;

import static com.google.common.base.Strings.emptyToNull;

/**
 */
public class SettingsActivity extends AppCompatActivity implements RoboContext {
    private final Map<Key<?>, Object> scopedObjectMap = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String category = null;
        String action = getIntent().getAction();
        if (action != null && action.startsWith("preference://"))
            category = emptyToNull(action.substring("preference://".length()));

        if (savedInstanceState == null) {
            SettingsFragment fragment = new SettingsFragment();
            fragment.setArguments(AndroidUtility.bundle("category", category));

            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public Map<Key<?>, Object> getScopedObjectMap() {
        return scopedObjectMap;
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            String category = getArguments().getString("category");
            if (category != null) {
                Preference root = getPreferenceManager().findPreference(category);
                if (root != null) {
                    getActivity().setTitle(root.getTitle());
                    setPreferenceScreen((PreferenceScreen) root);
                }
            }

            // Load the preferences from an XML resource
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
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, @NonNull Preference preference) {
            if ("pref_pseudo_update".equals(preference.getKey())) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                UpdateDialogFragment.checkForUpdates(activity, true);
                return true;
            }

            if ("pref_pseudo_changelog".equals(preference.getKey())) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                ChangeLogDialog dialog = new ChangeLogDialog();
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            }

            if ("pref_pseudo_feedback".equals(preference.getKey())) {
                // open google form
                String version = Pr0grammApplication.getPackageInfo(getActivity()).versionName;
                String url = "https://docs.google.com/forms/d/1YVZDzaoaeNDncbxv7qKWlp067yUtUtCz5lqpCo0bcFc/viewform?entry.409811813=" + version;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

            if ("pref_pseudo_logcat".equals(preference.getKey())) {
                Optional<File> logFile = LogcatUtility.dump();
                if (logFile.isPresent()) {
                    DialogBuilder.start(getActivity())
                            .content(getString(R.string.logcat_logfile_created, logFile.get()))
                            .positive(R.string.okay)
                            .show();

                } else {
                    DialogBuilder.start(getActivity())
                            .content(getString(R.string.logcat_error_occurred))
                            .positive(R.string.okay)
                            .show();
                }
            }

            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            updateContentTypeBoxes(preferences);

            if ("pref_convert_gif_to_webm".equals(key)) {
                if (preferences.getBoolean("pref_convert_gif_to_webm", false)) {
                    DialogBuilder.start(getActivity())
                            .content(R.string.gif_as_webm_might_be_buggy)
                            .positive(R.string.okay)
                            .show();
                }
            }

            if ("pref_hardware_acceleration".equals(key)) {
                DialogBuilder.start(getActivity())
                        .content(R.string.need_to_restart_app)
                        .positive(R.string.okay)
                        .show();
            }
        }

        private void updateContentTypeBoxes(SharedPreferences sharedPreferences) {
            Settings prefs = Settings.of(sharedPreferences);
            boolean enabled = prefs.getContentType().size() > 1;
            List<String> contentTypeKeys = ImmutableList.of(
                    "pref_feed_type_sfw", "pref_feed_type_nsfw", "pref_feed_type_nsfl");

            for (String ctKey : contentTypeKeys) {
                Preference pref = findPreference(ctKey);
                if (pref != null && sharedPreferences.getBoolean(ctKey, false))
                    pref.setEnabled(enabled);
            }
        }
    }
}
