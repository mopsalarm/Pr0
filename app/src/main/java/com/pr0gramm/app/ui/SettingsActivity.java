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
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.CustomProxySelector;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.UnlockService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.preloading.PreloadManager;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.util.AndroidUtility;
import com.squareup.okhttp.OkHttpClient;

import org.joda.time.Instant;

import java.util.List;

import javax.inject.Inject;

import de.psdev.licensesdialog.LicensesDialog;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.google.common.base.Strings.emptyToNull;

/**
 */
public class SettingsActivity extends BaseAppCompatActivity {
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
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Inject
        UserService userService;

        @Inject
        Settings settings;

        @Inject
        PreloadManager preloadManager;

        @Inject
        OkHttpClient okHttpClient;

        @Inject
        UnlockService unlockService;

        private Subscription preloadItemsSubscription;
        public static final List<String> CONTENT_TYPE_KEYS = ImmutableList.of(
                "pref_feed_type_sfw", "pref_feed_type_nsfw", "pref_feed_type_nsfl");

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Dagger.appComponent(getActivity()).inject(this);

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

            if (!BuildConfig.DEBUG) {
                hideDebugPreferences();
            }
        }

        private void hideDebugPreferences() {
            Preference pref = findPreference("prefcat_debug");
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
            }
        }

        private void updatePreloadInfo() {
            Preference preference = getPreferenceManager().findPreference("pref_pseudo_clean_preloaded");
            if (preference != null) {
                preloadItemsSubscription = preloadManager.all()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(items -> {

                            long totalSize = 0;
                            for (PreloadManager.PreloadItem item : items) {
                                totalSize += item.media().length();
                                totalSize += item.thumbnail().length();
                            }

                            preference.setSummary(getString(R.string.pseudo_clean_preloaded_summary_with_size,
                                    totalSize / (1024.f * 1024.f)));
                        });
            }
        }

        private void updateFlavorSettings() {
            if (contentTypesNotVisible()) {
                Preference pref = findPreference("prefcat_feed_types");
                if (pref != null) {
                    getPreferenceScreen().removePreference(pref);
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen()
                    .getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);

            updatePreloadInfo();
        }

        @Override
        public void onPause() {
            getPreferenceScreen()
                    .getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);

            if (preloadItemsSubscription != null) {
                preloadItemsSubscription.unsubscribe();
                preloadItemsSubscription = null;
            }

            super.onPause();
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, @NonNull Preference preference) {
            if ("pref_pseudo_update".equals(preference.getKey())) {
                BaseAppCompatActivity activity = (BaseAppCompatActivity) getActivity();
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

            if ("pref_pseudo_recommend".equals(preference.getKey())) {
                String text = "Probiere mal die pr0gramm App aus: https://mopsalarm.github.io/";
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, "pr0gramm app");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, getString(R.string.share_using)));
            }

            if ("pref_pseudo_clean_preloaded".equals(preference.getKey())) {
                Async.start(() -> {
                    // remove all the files!
                    preloadManager.deleteBefore(Instant.now());
                    return null;
                }, Schedulers.io());
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
                            .positive()
                            .show();
                }
            }

            //noinspection PointlessBooleanExpression,ConstantConditions
            if ("pref_use_beta_channel".equals(key) && BuildConfig.IS_PLAYSTORE_RELEASE) {
                if (preferences.getBoolean("pref_use_beta_channel", true)) {
                    DialogBuilder.start(activity)
                            .content(R.string.beta_you_need_to_join_community)
                            .positive(R.string.okay, di -> Pr0grammApplication.openCommunityWebpage(activity))
                            .show();
                }
            }

            if ("pref_use_api_proxy".equals(key)) {
                boolean useProxy = preferences.getBoolean(key, false);
                okHttpClient.setProxySelector(useProxy ? new CustomProxySelector() : null);

                if (useProxy) {
                    DialogBuilder.start(activity)
                            .content(R.string.warn_api_proxy)
                            .positive()
                            .show();
                }
            }
        }

        private void updateContentTypeBoxes(SharedPreferences sharedPreferences) {
            Settings settings = Settings.of(sharedPreferences);
            boolean enabled = settings.getContentType().size() > 1 && userService.isAuthorized() && !contentTypesNotVisible();

            for (String ctKey : CONTENT_TYPE_KEYS) {
                Preference pref = findPreference(ctKey);
                if (pref != null && sharedPreferences.getBoolean(ctKey, false))
                    pref.setEnabled(enabled);
            }

            if (!userService.isAuthorized()) {
                for (String name : new String[]{"pref_feed_type_nsfw", "pref_feed_type_nsfl"}) {
                    Preference pref = findPreference(name);
                    if (pref != null) {
                        pref.setEnabled(false);
                        pref.setSummary(R.string.pref_feed_type_login_required);
                    }
                }
            }
        }

        private boolean contentTypesNotVisible() {
            return !unlockService.unlocked();
        }
    }
}
