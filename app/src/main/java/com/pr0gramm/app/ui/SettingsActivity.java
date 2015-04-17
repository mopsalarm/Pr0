package com.pr0gramm.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;

import java.util.List;

import roboguice.activity.RoboActionBarActivity;

/**
 */
public class SettingsActivity extends RoboActionBarActivity {
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
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, @NonNull Preference preference) {
            if ("pref_pseudo_update".equals(preference.getKey())) {
                RoboActionBarActivity activity = (RoboActionBarActivity) getActivity();
                UpdateDialogFragment.checkForUpdates(activity, true);
                return true;
            }

            if ("pref_pseudo_changelog".equals(preference.getKey())) {
                RoboActionBarActivity activity = (RoboActionBarActivity) getActivity();
                ChangeLogDialog dialog = new ChangeLogDialog();
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            }

            if ("pref_pseudo_feedback".equals(preference.getKey())) {
                // open google form
                String url = "https://docs.google.com/forms/d/1YVZDzaoaeNDncbxv7qKWlp067yUtUtCz5lqpCo0bcFc/viewform";
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

            if ("pref_pseudo_logcat".equals(preference.getKey())) {
                dumpLogcatToFile();
            }

            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            updateContentTypeBoxes(preferences);

            if ("pref_convert_gif_to_webm".equals(key)) {
                if (preferences.getBoolean("pref_convert_gif_to_webm", false)) {
                    new MaterialDialog.Builder(getActivity())
                            .content(R.string.gif_as_webm_might_be_buggy)
                            .positiveText(R.string.okay)
                            .show();
                }
            }

            if ("pref_hardware_acceleration".equals(key)) {
                new MaterialDialog.Builder(getActivity())
                        .content(R.string.need_to_restart_app)
                        .positiveText(R.string.okay)
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

                if (sharedPreferences.getBoolean(ctKey, false))
                    pref.setEnabled(enabled);
            }
        }

        /**
         * Dumps the logcat to a file
         */
        private void dumpLogcatToFile() {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "sh", "-c", "logcat -d > /sdcard/pr0gramm.log"
                });

                process.waitFor();

                new MaterialDialog.Builder(getActivity())
                        .content("Logfile in /sdcard/pr0gramm.txt angelegt.")
                        .positiveText(R.string.okay)
                        .show();

            } catch (Exception err) {
                new MaterialDialog.Builder(getActivity())
                        .content("Fehler: " + err.getMessage())
                        .positiveText(R.string.okay)
                        .show();
            }
        }
    }

}
