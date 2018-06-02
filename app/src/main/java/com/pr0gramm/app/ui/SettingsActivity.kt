package com.pr0gramm.app.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.common.base.Strings.emptyToNull
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.RecentSearchesServices
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment
import com.pr0gramm.app.ui.intro.IntroActivity
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.bundle
import com.pr0gramm.app.util.doInBackground
import com.trello.rxlifecycle.components.RxPreferenceFragment
import org.joda.time.Instant.now
import rx.android.schedulers.AndroidSchedulers

/**
 */
class SettingsActivity : BaseAppCompatActivity("SettingsActivity") {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        var category: String? = null
        val action = intent.action
        if (action != null && action.startsWith("preference://"))
            category = emptyToNull(action.substring("preference://".length))

        if (savedInstanceState == null) {
            val fragment = SettingsFragment()
            fragment.arguments = bundle {
                putString("category", category)
            }

            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : RxPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val settings = Settings.get()

        private val k = LazyKodein { activity.appKodein().kodein }
        private val userService: UserService by k.instance()
        private val preloadManager: PreloadManager by k.instance()
        private val recentSearchesServices: RecentSearchesServices by k.instance()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            if (!userService.isAuthorized) {
                // reset those content types - better be sure!
                settings.resetContentTypeSettings()
            }

            addPreferencesFromResource(R.xml.preferences)

            updateCodecPreference("pref_video_codec", "video/avc")
            updateCodecPreference("pref_audio_codec", "audio/mp4a-latm")

            val category = arguments.getString("category")
            if (category != null) {
                val root = preferenceManager.findPreference(category)
                if (root != null) {
                    activity.title = root.title
                    preferenceScreen = root as PreferenceScreen
                }
            }

            if (!BuildConfig.DEBUG) {
                hidePreferenceByName("prefcat_debug")
            }

            if (!userService.userIsAdmin) {
                hidePreferenceByName("pref_show_content_type_flag")
            }
        }

        private fun hidePreferenceByName(name: String) {
            val pref = preferenceScreen.findPreference(name)
            if (pref != null) {
                preferenceScreen.removePreference(pref)

                for (idx in 0 until preferenceScreen.preferenceCount) {
                    val preference = preferenceScreen.getPreference(idx)
                    if (preference is PreferenceGroup) {
                        if (preference.removePreference(pref))
                            break
                    }
                }
            }
        }

        private fun updateCodecPreference(prefName: String, mimeType: String) {
            val entries = mutableListOf<CharSequence>()
            val entryValues = mutableListOf<CharSequence>()

            val pref = findPreference(prefName) as ListPreference? ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                entries.add("Software")
                entryValues.add("software")

                entries.add("Hardware")
                entryValues.add("hardware")

                try {
                    val codecs = MediaCodecUtil.getDecoderInfos(mimeType, false)
                    for (codec in codecs) {
                        entries.add(codec.name.toLowerCase())
                        entryValues.add(codec.name)
                    }
                } catch (ignored: MediaCodecUtil.DecoderQueryException) {
                }
            }

            if (entries.size > 3) {
                pref.setDefaultValue("hardware")
                pref.entries = entries.toTypedArray()
                pref.entryValues = entryValues.toTypedArray()
            } else {
                pref.isEnabled = false
            }
        }

        private fun updatePreloadInfo() {
            val preference = preferenceManager.findPreference("pref_pseudo_clean_preloaded")
            if (preference != null) {
                preloadManager.all()
                        .subscribeOn(BackgroundScheduler.instance())
                        .map { items -> items.fold(0L) { sum, item -> sum + item.media.length() + item.thumbnail.length() } }
                        .observeOn(AndroidSchedulers.mainThread())
                        .compose(bindToLifecycle())
                        .subscribe { totalSize ->
                            preference.summary = getString(
                                    R.string.pseudo_clean_preloaded_summary_with_size,
                                    totalSize / (1024f * 1024f))
                        }
            }
        }

        override fun onResume() {
            super.onResume()

            preferenceScreen.sharedPreferences
                    .registerOnSharedPreferenceChangeListener(this)

            updatePreloadInfo()
        }

        override fun onPause() {
            preferenceScreen.sharedPreferences
                    .unregisterOnSharedPreferenceChangeListener(this)

            super.onPause()
        }

        override fun onPreferenceTreeClick(preferenceScreen: PreferenceScreen, preference: Preference): Boolean {
            when (preference.key) {
                "pref_pseudo_update" -> {
                    val activity = activity as BaseAppCompatActivity
                    UpdateDialogFragment.checkForUpdates(activity, true)
                    return true
                }

                "pref_pseudo_changelog" -> {
                    val activity = activity as AppCompatActivity
                    ChangeLogDialog().show(activity.supportFragmentManager, null)
                    return true
                }

                "pref_pseudo_recommend" -> {
                    val text = "Probiere mal die offizielle pr0gramm App aus: https://app.pr0gramm.com/"
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_SUBJECT, "pr0gramm app")
                    intent.putExtra(Intent.EXTRA_TEXT, text)
                    startActivity(Intent.createChooser(intent, getString(R.string.share_using)))
                    return true
                }

                "pref_pseudo_clean_preloaded" -> {
                    doInBackground { preloadManager.deleteBefore(now()) }
                    return true
                }

                "pref_pseudo_clear_tag_suggestions" -> {
                    recentSearchesServices.clearHistory()
                    return true
                }

                "pref_pseudo_onboarding" -> {
                    startActivity(Intent(activity, IntroActivity::class.java))
                    return true
                }
            }

            return super.onPreferenceTreeClick(preferenceScreen, preference)
        }

        override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
            // get the correct theme for the app!
            when (key) {
                "pref_convert_gif_to_webm" -> if (preferences.getBoolean(key, false)) {
                    showDialog(activity) {
                        content(R.string.gif_as_webm_might_be_buggy)
                        positive()
                    }
                }

                "pref_theme" -> {
                    // get the correct theme for the app!
                    ThemeHelper.updateTheme()
                    AndroidUtility.recreateActivity(activity)
                }
            }
        }
    }
}
