package com.pr0gramm.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.ApplicationClass;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.CustomProxySelector;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.RecentSearchesServices;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.preloading.PreloadManager;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.okhttp.OkHttpClient;

import java.util.List;

import javax.inject.Inject;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.util.async.Async;

import static com.google.common.base.Strings.emptyToNull;
import static com.pr0gramm.app.services.ThemeHelper.theme;
import static org.joda.time.Instant.now;

/**
 */
public class SettingsActivity extends BaseAppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
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
        RecentSearchesServices recentSearchesServices;

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
                        .subscribeOn(BackgroundScheduler.instance())
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
            String preferenceKey = preference.getKey();
            if ("pref_pseudo_update".equals(preferenceKey)) {
                BaseAppCompatActivity activity = (BaseAppCompatActivity) getActivity();
                UpdateDialogFragment.checkForUpdates(activity, true);
                return true;
            }

            if ("pref_pseudo_changelog".equals(preferenceKey)) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                ChangeLogDialog dialog = new ChangeLogDialog();
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            }

            if ("pref_pseudo_feedback".equals(preferenceKey)) {
                startActivity(new Intent(getActivity(), FeedbackActivity.class));
                return true;
            }

            if ("pref_pseudo_recommend".equals(preferenceKey)) {
                String text = "Probiere mal die offizielle pr0gramm App aus: https://app.pr0gramm.com/";
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, "pr0gramm app");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, getString(R.string.share_using)));
            }

            if ("pref_pseudo_clean_preloaded".equals(preferenceKey)) {
                Async.start(() -> {
                    // remove all the files!
                    preloadManager.deleteBefore(now());
                    return null;
                }, BackgroundScheduler.instance());
            }

            if ("pref_pseudo_clear_tag_suggestions".equals(preferenceKey)) {
                recentSearchesServices.clearHistory();
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
                            .positive(R.string.okay, di -> ApplicationClass.openCommunityWebpage(activity))
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

            if ("pref_theme".equals(key)) {
                // get the correct theme for the app!
                ThemeHelper.updateTheme(getActivity());

                final Intent intent = getActivity().getIntent();
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                TaskStackBuilder.create(getActivity())
                        .addNextIntentWithParentStack(intent)
                        .startActivities();
            }
        }

        private void updateContentTypeBoxes(SharedPreferences sharedPreferences) {
            Settings settings = Settings.of(sharedPreferences);
            boolean enabled = settings.getContentType().size() > 1 && userService.isAuthorized();

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
    }
}
