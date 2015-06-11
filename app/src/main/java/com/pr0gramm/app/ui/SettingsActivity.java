package com.pr0gramm.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.psdev.licensesdialog.LicensesDialog;
import roboguice.RoboGuice;
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
        @Inject
        private UserService userService;

        @Inject
        private Settings settings;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            RoboGuice.getInjector(getActivity()).injectMembersWithoutViews(this);

            if (!userService.isAuthorized()) {
                // reset those content types - better be sure!
                Settings.resetContentTypeSettings(settings);
            }

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
            updateFlavorSettings();
        }

        private void updateFlavorSettings() {
            // customize depending on build flavor.
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
                startActivity(new Intent(getActivity(), FeedbackActivity.class));
                return true;
            }

            if ("pref_pseudo_licenses".equals(preference.getKey())) {
                new LicensesDialog.Builder(getActivity()).setNotices(R.raw.licenses)
                        .setIncludeOwnLicense(true).build().show();
                return true;
            }

            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            Activity activity = getActivity();
            updateContentTypeBoxes(preferences);

            if ("pref_convert_gif_to_webm".equals(key)) {
                if (preferences.getBoolean("pref_convert_gif_to_webm", false)) {
                    DialogBuilder.start(activity)
                            .content(R.string.gif_as_webm_might_be_buggy)
                            .positive(R.string.okay)
                            .show();
                }
            }

            //noinspection PointlessBooleanExpression,ConstantConditions
            if("pref_use_beta_channel".equals(key) && BuildConfig.IS_PLAYSTORE_RELEASE) {
                if (preferences.getBoolean("pref_use_beta_channel", true)) {
                    DialogBuilder.start(activity)
                            .content(R.string.beta_you_need_to_join_community)
                            .positive(R.string.okay, di -> Pr0grammApplication.openCommunityWebpage(activity))
                            .show();
                }
            }
        }

        private void updateContentTypeBoxes(SharedPreferences sharedPreferences) {
            Settings settings = Settings.of(sharedPreferences);
            boolean enabled = settings.getContentType().size() > 1 && userService.isAuthorized();
            List<String> contentTypeKeys = ImmutableList.of(
                    "pref_feed_type_sfw", "pref_feed_type_nsfw", "pref_feed_type_nsfl");

            for (String ctKey : contentTypeKeys) {
                Preference pref = findPreference(ctKey);
                if (pref != null && sharedPreferences.getBoolean(ctKey, false))
                    pref.setEnabled(enabled);
            }

            if(!userService.isAuthorized()) {
                for (String name : new String[]{"pref_feed_type_nsfw", "pref_feed_type_nsfl"}) {
                    Preference pref = findPreference(name);
                    if (pref != null) {
                        pref.setEnabled(false);
                        pref.setSummary(R.string.pref_feed_type_login_required);
                    }
                }
            }
        }
    }
}
